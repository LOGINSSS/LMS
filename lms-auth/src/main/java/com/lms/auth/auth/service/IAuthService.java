package com.lms.auth.auth.service;

import com.lms.auth.auth.domain.dto.LoginFormDTO;
import com.lms.auth.auth.domain.dto.LoginVO;
import com.lms.auth.auth.domain.dto.RegisterFormDTO;
import com.lms.auth.auth.domain.dto.UserInfoVO;
import jakarta.servlet.http.HttpServletRequest;

/**
 * 认证服务接口：注册 / 登录 / 登出 / 当前用户信息
 *
 * 业务域：承载账号生命周期（注册 → 登录 → 登出）与登录态查询，
 * 是 lms-auth 模块对外暴露的核心业务门面。
 */
public interface IAuthService {

    /**
     * 注册（学生/教师）：创建登录账号，并同步在 lms-user 创建用户档案
     *
     * 业务背景：注册 = 账号落库 + Feign 创建档案两步在同一本地事务；
     * 档案创建失败（降级抛 REGISTER_FAILED）→ 事务回滚，账号一并撤销，保证账号与档案一致。
     *
     * @param dto 注册表单（用户名、密码、用户类型及按类型区分的扩展资料）
     * @throws CommonException 用户名已存在（ACCOUNT_EXISTS）、档案创建失败（REGISTER_FAILED）
     */
    void register(RegisterFormDTO dto);

    /**
     * 账号密码登录：校验凭据，签发 JWT，记录登录日志
     *
     * 业务背景：账号不存在与密码错误统一提示（防账号枚举）；状态非正常拒绝登录；
     * 校验通过后签发携带 jti 的 JWT（供登出黑名单），成功/失败均记录登录日志。
     *
     * @param dto     登录表单（用户名 + 明文密码）
     * @param request 当前请求对象（取客户端 IP / UA 记录日志）
     * @return 登录结果：JWT token + 用户档案 id + 用户类型 + 登录账号
     * @throws CommonException 登录失败（LOGIN_FAILED）、账号被禁用（ACCOUNT_DISABLED）
     */
    LoginVO login(LoginFormDTO dto, HttpServletRequest request);

    /**
     * 登出：将 token 的 jti 加入 Redis 黑名单，实现主动失效
     *
     * 业务背景：token 在有效期内也可被主动失效——把 jti 写入黑名单（TTL 与 token 有效期一致），
     * 网关/资源服务校验时命中黑名单即拒绝。
     *
     * @param authorization Authorization 请求头（含 Bearer 前缀，可能为 null）
     * @throws CommonException token 缺失、格式错误或无效（TOKEN_INVALID）
     */
    void logout(String authorization);

    /**
     * 当前登录用户基本信息：解析 token 并返回
     *
     * 业务背景：仅解析 token 载荷返回用户档案 id / 用户类型 / 登录账号，不查库，
     * 用于前端展示登录态与用户类型。
     *
     * @param authorization Authorization 请求头（含 Bearer 前缀，可能为 null）
     * @return 用户基本信息（解析 token 所得）
     * @throws CommonException token 缺失、格式错误或无效（TOKEN_INVALID）
     */
    UserInfoVO me(String authorization);
}
