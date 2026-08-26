package com.lms.auth.auth.service.impl;

import cn.hutool.crypto.digest.BCrypt;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lms.auth.auth.client.UserClient;
import com.lms.auth.auth.client.dto.UserProfileDTO;
import com.lms.auth.auth.constants.AuthErrorInfo;
import com.lms.auth.auth.constants.RedisConstants;
import com.lms.auth.auth.domain.dto.LoginFormDTO;
import com.lms.auth.auth.domain.dto.LoginVO;
import com.lms.auth.auth.domain.dto.RegisterFormDTO;
import com.lms.auth.auth.domain.dto.UserInfoVO;
import com.lms.auth.auth.domain.po.Account;
import com.lms.auth.auth.domain.po.LoginLog;
import com.lms.auth.auth.enums.UserStatus;
import com.lms.auth.auth.mapper.AccountMapper;
import com.lms.auth.auth.mapper.LoginLogMapper;
import com.lms.auth.auth.service.IAuthService;
import com.lms.common.constants.Constant;
import com.lms.common.exceptions.CommonException;
import com.lms.common.utils.JwtUtils;
import com.lms.common.utils.StringUtils;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 认证服务实现
 *
 * 业务规则：
 * - 注册：账号落库 + Feign 创建档案两步在同一本地事务；档案创建失败（降级抛 REGISTER_FAILED）→ 事务回滚，账号一并撤销
 * - 登录：账号不存在与密码错误统一提示（防账号枚举）；状态非正常拒绝登录；密码 BCrypt 校验；成功签发 JWT（携带 jti 供登出黑名单）
 * - 登出：将 token 的 jti 写入 Redis 黑名单（TTL 与 token 有效期一致），实现主动失效
 * - 登录日志：成功/失败均记录，日志写入失败仅告警，不影响登录主流程
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements IAuthService {

    private final AccountMapper accountMapper;
    private final LoginLogMapper loginLogMapper;
    private final StringRedisTemplate stringRedisTemplate;
    private final UserClient userClient;

    /** JWT 签名密钥（Nacos lms-common.yaml 配置） */
    @Value("${lms.jwt.secret}")
    private String jwtSecret;

    /** JWT 有效期（秒），默认 1 天 */
    @Value("${lms.jwt.ttl:86400}")
    private long jwtTtl;

    /**
     * 注册账号（本地事务）
     *
     * 业务背景：注册 = 账号落库 + Feign 创建档案两步在同一 @Transactional 本地事务；
     * 档案创建失败（降级抛 REGISTER_FAILED）→ 整体回滚，账号一并撤销，保证账号与档案一致。
     *
     * @param dto 注册表单（用户名、密码、用户类型及按类型区分的扩展资料）
     * @throws CommonException 用户名已存在（ACCOUNT_EXISTS）、档案创建失败（REGISTER_FAILED）
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void register(RegisterFormDTO dto) {
        //1. 用户名唯一性预校验：重复用户名直接友好提示，避免走到落库再失败
        Long count = accountMapper.selectCount(
                new LambdaQueryWrapper<Account>().eq(Account::getUsername, dto.getUsername()));
        if (count != null && count > 0) {
            throw new CommonException(AuthErrorInfo.ACCOUNT_EXISTS);
        }
        // 注意：预校验存在并发窗口，最终兜底是 account 表 uk_username 唯一索引，撞索引即注册失败
        //2. 落库账号：密码 BCrypt 加密存储，初始状态为正常，user_id 暂为空待回填
        Account account = new Account();
        account.setUsername(dto.getUsername());
        account.setPassword(BCrypt.hashpw(dto.getPassword(), BCrypt.gensalt()));
        account.setUserType(dto.getUserType());
        account.setStatus(UserStatus.NORMAL.getValue());
        accountMapper.insert(account);
        //3. 同步调用 lms-user 创建档案：失败时由降级工厂抛 REGISTER_FAILED
        //   【保障机制】事务回滚：异常沿调用链传播使本方法 @Transactional 整体回滚，账号一并撤销
        Long profileId = userClient.createUserProfile(buildProfileDTO(account.getId(), dto)).getData();
        //4. 回填档案 id：将 lms-user 返回的档案 id 写回 account.user_id，完成账号与档案的关联
        account.setUserId(profileId);
        accountMapper.updateById(account);
    }

    /**
     * 账号密码登录
     *
     * 业务背景：账号不存在与密码错误统一提示（防账号枚举）；状态非正常拒绝登录；
     * 校验通过后签发携带 jti 的 JWT（供登出黑名单），并记录登录日志。
     *
     * @param dto     登录表单（用户名 + 明文密码）
     * @param request 当前请求（取客户端 IP / UA 记录日志）
     * @return 登录结果：token + 用户档案 id + 用户类型 + 登录账号
     * @throws CommonException 登录失败（LOGIN_FAILED）、账号被禁用（ACCOUNT_DISABLED）
     */
    @Override
    public LoginVO login(LoginFormDTO dto, HttpServletRequest request) {
        //1. 按用户名查账号：账号不存在与密码错误统一提示（防账号枚举）
        Account account = accountMapper.selectOne(
                new LambdaQueryWrapper<Account>().eq(Account::getUsername, dto.getUsername()));
        if (account == null) {
            throw new CommonException(AuthErrorInfo.LOGIN_FAILED);
        }
        //2. 状态校验：账号状态非正常（禁用）时拒绝登录
        if (account.getStatus() == null || account.getStatus() != UserStatus.NORMAL.getValue()) {
            throw new CommonException(AuthErrorInfo.ACCOUNT_DISABLED);
        }
        //3. 密码校验（BCrypt）：校验失败记录失败日志后统一提示，与账号不存在同文案
        if (!BCrypt.checkpw(dto.getPassword(), account.getPassword())) {
            saveLoginLog(account, request, 0);
            throw new CommonException(AuthErrorInfo.LOGIN_FAILED);
        }
        //4. 记录成功日志并签发 JWT：token 携带 jti，供登出时写入黑名单实现主动失效
        saveLoginLog(account, request, 1);
        String token = JwtUtils.createToken(account.getUserId(), account.getUserType(),
                account.getUsername(), jwtSecret, jwtTtl);
        LoginVO vo = new LoginVO();
        vo.setToken(token);
        vo.setUserId(account.getUserId());
        vo.setUserType(account.getUserType());
        vo.setUsername(account.getUsername());
        return vo;
    }

    /**
     * 登出：将 token 的 jti 写入 Redis 黑名单，实现主动失效
     *
     * @param authorization Authorization 请求头（含 Bearer 前缀，可能为 null）
     * @throws CommonException token 缺失、格式错误或无效（TOKEN_INVALID）
     */
    @Override
    public void logout(String authorization) {
        String token = resolveToken(authorization);
        Object jti = parseClaims(token).get(JwtUtils.CLAIM_JTI);
        if (jti == null) {
            throw new CommonException(AuthErrorInfo.TOKEN_INVALID);
        }
        // 【保障机制】黑名单登出：jti 写入 Redis 黑名单，TTL 与 token 有效期一致（86400 秒），
        // 网关/资源服务校验时命中黑名单即拒绝，token 在有效期内也能被主动失效
        stringRedisTemplate.opsForValue().set(
                RedisConstants.JWT_BLACKLIST_KEY + jti, "1",
                RedisConstants.JWT_BLACKLIST_TTL, TimeUnit.SECONDS);
    }

    /**
     * 当前登录用户基本信息：解析 token 载荷直接返回（不查库）
     *
     * @param authorization Authorization 请求头（含 Bearer 前缀，可能为 null）
     * @return 用户基本信息（用户档案 id / 用户类型 / 登录账号）
     * @throws CommonException token 缺失、格式错误或无效（TOKEN_INVALID）
     */
    @Override
    public UserInfoVO me(String authorization) {
        //1. 解析并校验 token：验签 + 过期校验，失败统一抛 TOKEN_INVALID
        String token = resolveToken(authorization);
        Map<String, Object> claims = parseClaims(token);
        //2. 提取用户载荷：user_id / user_type 缺失视为无效 token
        Number userIdNum = (Number) claims.get(JwtUtils.CLAIM_USER_ID);
        Number userTypeNum = (Number) claims.get(JwtUtils.CLAIM_USER_TYPE);
        if (userIdNum == null || userTypeNum == null) {
            throw new CommonException(AuthErrorInfo.TOKEN_INVALID);
        }
        //3. 组装返回结果：仅取 token 载荷，不查库
        UserInfoVO vo = new UserInfoVO();
        vo.setUserId(userIdNum.longValue());
        vo.setUserType(userTypeNum.intValue());
        vo.setUsername((String) claims.get(JwtUtils.CLAIM_USERNAME));
        return vo;
    }

    /**
     * 从 Authorization 请求头解析出裸 token（去掉 Bearer 前缀）
     *
     * 业务规则：请求头缺失或不以 Bearer 前缀开头 → 抛 TOKEN_INVALID
     *
     * @param authorization Authorization 请求头
     * @return 裸 token 字符串
     * @throws CommonException token 缺失或格式错误（TOKEN_INVALID）
     */
    private String resolveToken(String authorization) {
        if (StringUtils.isBlank(authorization) || !authorization.startsWith(Constant.TOKEN_PREFIX)) {
            throw new CommonException(AuthErrorInfo.TOKEN_INVALID);
        }
        return authorization.substring(Constant.TOKEN_PREFIX.length());
    }

    /**
     * 解析并校验 token（验签 + 过期），失败统一抛 TOKEN_INVALID
     *
     * @param token 裸 token 字符串
     * @return token 载荷（含 user_id / user_type / username / jti）
     * @throws CommonException 验签失败或已过期（TOKEN_INVALID）
     */
    private Map<String, Object> parseClaims(String token) {
        try {
            return JwtUtils.parseClaims(token, jwtSecret);
        } catch (Exception e) {
            log.warn("token 解析失败：{}", e.getMessage());
            throw new CommonException(AuthErrorInfo.TOKEN_INVALID);
        }
    }

    /**
     * 组装用户档案 DTO：作为 Feign 调用 createUserProfile 的请求体
     *
     * 字段与 lms-user 服务端约定一致；扩展字段按用户类型透传
     * （教师：college/title/bio；学生：studentNo/major/grade/className）。
     *
     * @param accountId 登录账号 id（lms-user 侧反查账号用）
     * @param dto       注册表单
     * @return 用户档案 DTO
     */
    private UserProfileDTO buildProfileDTO(Long accountId, RegisterFormDTO dto) {
        UserProfileDTO profile = new UserProfileDTO();
        profile.setAccountId(accountId);
        profile.setUserType(dto.getUserType());
        profile.setNickname(dto.getNickname());
        profile.setPhone(dto.getPhone());
        profile.setEmail(dto.getEmail());
        profile.setCollege(dto.getCollege());
        profile.setTitle(dto.getTitle());
        profile.setBio(dto.getBio());
        profile.setStudentNo(dto.getStudentNo());
        profile.setMajor(dto.getMajor());
        profile.setGrade(dto.getGrade());
        profile.setClassName(dto.getClassName());
        return profile;
    }

    /**
     * 记录登录日志（成功/失败均记录）
     *
     * 业务规则：日志写入失败仅告警，不影响登录主流程（登录结果以主流程为准）。
     *
     * @param account 登录账号（取账号 id / 档案 id / 用户类型）
     * @param request 当前请求（取客户端 IP / UA）
     * @param status  登录结果：1 成功 0 失败
     */
    private void saveLoginLog(Account account, HttpServletRequest request, int status) {
        try {
            LoginLog loginLog = new LoginLog();
            loginLog.setAccountId(account.getId());
            loginLog.setUserId(account.getUserId());
            loginLog.setUserType(account.getUserType());
            loginLog.setLoginTime(LocalDateTime.now());
            loginLog.setIp(request.getRemoteAddr());
            loginLog.setDevice(request.getHeader("User-Agent"));
            loginLog.setStatus(status);
            loginLogMapper.insert(loginLog);
        } catch (Exception e) {
            log.error("记录登录日志失败，accountId={}", account.getId(), e);
        }
    }
}
