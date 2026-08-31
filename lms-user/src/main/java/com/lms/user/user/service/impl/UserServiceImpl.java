package com.lms.user.user.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lms.common.domain.dto.PageDTO;
import com.lms.common.enums.UserType;
import com.lms.common.exceptions.CommonException;
import com.lms.common.utils.AssertUtils;
import com.lms.common.utils.BeanUtils;
import com.lms.common.utils.UserContext;
import com.lms.user.user.constants.UserErrorInfo;
import com.lms.user.user.domain.dto.UserDetailVO;
import com.lms.user.user.domain.dto.UserProfileFormDTO;
import com.lms.user.user.domain.dto.UserVO;
import com.lms.user.user.domain.po.StudentInfo;
import com.lms.user.user.domain.po.TeacherInfo;
import com.lms.user.user.domain.po.User;
import com.lms.user.user.domain.query.UserPageQuery;
import com.lms.user.user.mapper.StudentInfoMapper;
import com.lms.user.user.mapper.TeacherInfoMapper;
import com.lms.user.user.mapper.UserMapper;
import com.lms.user.user.service.IUserService;
import com.lms.user.user.service.UserCacheService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

/**
 * 用户档案服务实现
 *
 * 承载档案创建、详情查询、资料修改、管理端分页等用户档案核心业务。
 * 事务边界：创建档案（user 主表 + 扩展表）、修改资料（user 主表 + 扩展表）
 * 均为单事务，任一写入失败整体回滚，保证主表与扩展表数据一致。
 *
 * 当前登录用户来源：UserContext（由公共拦截器 UserInfoInterceptor 解析网关透传的
 * user-info 头写入，本服务通过 Nacos 配置 lms.mvc.user-header-enabled=true 开启解析）。
 * 注意：ThreadLocal 不随异步线程传递，勿在子线程中读取 UserContext。
 */
@Service
@RequiredArgsConstructor
public class UserServiceImpl implements IUserService {

    private final UserMapper userMapper;
    private final TeacherInfoMapper teacherInfoMapper;
    private final StudentInfoMapper studentInfoMapper;
    private final UserCacheService cacheService;

    /**
     * 创建用户档案（幂等 + 事务）
     *
     * 业务背景：认证服务注册成功后内部 Feign 调用本方法建档，网络重试可能重复触发，
     * 因此按 accountId 先查后插，已建档直接返回已有 id；并发场景靠 uk_account_id
     * 唯一索引兜底，保证同一账号最多一份档案。
     *
     * 创建规则：user 主表 status 固定为 1（正常）；按 userType 写入对应扩展表
     * （TEACHER → teacher_info；STUDENT → student_info），扩展字段空字符串归一为 null 存储。
     *
     * @param dto 档案入参（accountId / userType 必填）
     * @return 用户档案 id（新建或已存在）
     * @throws CommonException 参数缺失、用户类型非法或插入失败时抛出
     */
    @Override
    @Transactional
    public Long createUserProfile(UserProfileFormDTO dto) {
        //1. 创建场景必填校验：accountId/userType 仅创建必填、修改可空，故不在 DTO 上加注解，这里按场景断言
        AssertUtils.notNull(dto, "档案参数不能为空");
        AssertUtils.notNull(dto.getAccountId(), "accountId 不能为空");
        AssertUtils.notNull(dto.getUserType(), "userType 不能为空");
        UserType userType = UserType.of(dto.getUserType());
        AssertUtils.isTrue(userType != null, "非法的用户类型");

        //2. 幂等校验：按 accountId 查已有档案，命中直接返回，避免认证服务 Feign 重试重复建档
        // 注意：先查后插非绝对并发安全，重复建档最终由 uk_account_id 唯一索引兜底
        User existed = userMapper.selectOne(new LambdaQueryWrapper<User>()
                .eq(User::getAccountId, dto.getAccountId()));
        if (existed != null) {
            return existed.getId();
        }

        //3. 插入 user 主表：档案基础字段落库，status 固定为 1（正常）
        User user = BeanUtils.copyBean(dto, User.class);
        user.setStatus(1);
        if (userMapper.insert(user) <= 0) {
            throw new CommonException(UserErrorInfo.PROFILE_CREATE_FAILED);
        }

        //4. 按用户类型写入对应扩展表：教师写 teacher_info、学生写 student_info，空字符串归一为 null
        switch (userType) {
            case TEACHER -> {
                TeacherInfo info = new TeacherInfo();
                info.setUserId(user.getId());
                info.setCollege(StrUtil.emptyToNull(dto.getCollege()));
                info.setTitle(StrUtil.emptyToNull(dto.getTitle()));
                info.setBio(StrUtil.emptyToNull(dto.getBio()));
                if (teacherInfoMapper.insert(info) <= 0) {
                    throw new CommonException(UserErrorInfo.PROFILE_CREATE_FAILED);
                }
            }
            case STUDENT -> {
                StudentInfo info = new StudentInfo();
                info.setUserId(user.getId());
                info.setStudentNo(StrUtil.emptyToNull(dto.getStudentNo()));
                info.setMajor(StrUtil.emptyToNull(dto.getMajor()));
                info.setGrade(StrUtil.emptyToNull(dto.getGrade()));
                info.setClassName(StrUtil.emptyToNull(dto.getClassName()));
                if (studentInfoMapper.insert(info) <= 0) {
                    throw new CommonException(UserErrorInfo.PROFILE_CREATE_FAILED);
                }
            }
            default -> {
                // 兜底分支：userType 已在上方断言合法且非 null，正常流程不会进入
            }
        }
        return user.getId();
    }

    /**
     * 当前登录用户详情
     *
     * 业务背景：本人中心页数据来源，用户 id 不靠入参传递，
     * 由公共拦截器解析网关透传的 user-info 头写入 UserContext，本方法直接读取。
     *
     * @return 当前用户详情（user 主表 + 对应扩展表字段聚合）
     * @throws CommonException 未登录或档案不存在时抛出
     */
    @Override
    public UserDetailVO getMyDetail() {
        //1. 取当前登录用户 id：来自 UserContext（拦截器写入），未登录则断言失败
        Long userId = UserContext.getUser();
        AssertUtils.isNotNull(userId, "未登录");
        //2. 按 id 查询档案并组装详情：主表字段 + 按 userType 查对应扩展表填充
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new CommonException(UserErrorInfo.USER_NOT_FOUND);
        }
        return toDetailVO(user);
    }

    /**
     * 修改当前用户资料（事务）
     *
     * 业务背景：本人资料编辑页提交，仅允许修改昵称、头像、手机号、邮箱四个主表字段
     * 及对应扩展表字段；accountId / userType 等身份字段不可改，故不用整体 copyBean 覆盖。
     *
     * 更新规则：主表仅更新非 null 字段（MyBatis-Plus 默认 NOT_NULL 策略）；
     * 扩展表以库中 userType 为准，存在则 update、不存在则 insert。
     *
     * @param dto 资料入参（仅非 null 字段参与更新）
     * @throws CommonException 未登录、档案不存在或主表更新失败时抛出
     */
    @Override
    @Transactional
    public void updateMyProfile(UserProfileFormDTO dto) {
        //1. 入参与登录态校验：本人接口的用户 id 取自 UserContext，不做入参传递
        AssertUtils.notNull(dto, "资料参数不能为空");
        Long userId = UserContext.getUser();
        AssertUtils.isNotNull(userId, "未登录");

        //2. 查询现有档案：以库中 userType 为准，决定后续更新哪张扩展表
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new CommonException(UserErrorInfo.USER_NOT_FOUND);
        }

        //3. 更新 user 主表：仅四个可编辑字段，null 不更新（MyBatis-Plus 默认 NOT_NULL 策略），
        //   避免整体 copyBean 误改 accountId / userType 等身份字段
        User update = new User();
        update.setId(userId);
        update.setNickname(dto.getNickname());
        update.setAvatar(dto.getAvatar());
        update.setPhone(dto.getPhone());
        update.setEmail(dto.getEmail());
        if (update.getNickname() != null || update.getAvatar() != null
                || update.getPhone() != null || update.getEmail() != null) {
            if (userMapper.updateById(update) <= 0) {
                throw new CommonException(UserErrorInfo.PROFILE_UPDATE_FAILED);
            }
        }

        //4. 按库中 userType 更新对应扩展表：教师更新 teacher_info、学生更新 student_info
        Integer userType = user.getUserType();
        if (userType != null && userType == UserType.TEACHER.getValue()) {
            updateTeacherInfo(userId, dto);
        } else if (userType != null && userType == UserType.STUDENT.getValue()) {
            updateStudentInfo(userId, dto);
        }
        //5. 失效用户信息缓存（各模块 Feign 读缓存，改后需主动删）
        cacheService.evict(userId);
    }

    /**
     * 按 id 查询用户详情
     *
     * 业务背景：管理端详情查看与内部服务按 id 查询共用，
     * 返回 user 主表 + 对应扩展表字段的聚合视图。
     *
     * @param id 用户档案 id
     * @return 用户详情 VO
     * @throws CommonException 档案不存在时抛出
     */
    @Override
    public UserDetailVO getUserDetail(Long id) {
        //1. 走缓存（Cache-Aside，spec 0.2 §5.1）：命中直接返回，未命中回源并回填
        return cacheService.getUserDetail(id, () -> {
            //1.1 按 id 查询档案：查不到直接抛 USER_NOT_FOUND，避免返回空详情
            User user = userMapper.selectById(id);
            if (user == null) {
                throw new CommonException(UserErrorInfo.USER_NOT_FOUND);
            }
            //1.2 组装详情 VO：主表字段 + 按 userType 查对应扩展表填充
            return toDetailVO(user);
        });
    }

    /**
     * 管理端分页查询用户
     *
     * 筛选规则：userType 非空则精确匹配；keyword 非空则对 nickname / phone / email
     * 三字段模糊匹配（或关系），供管理后台按类型或关键字检索用户。
     *
     * 排序：默认按创建时间倒序（toMpPageDefaultSortByCreateTimeDesc）。
     *
     * @param query 分页与筛选条件
     * @return 分页结果（UserVO 仅含主表公共字段，不含扩展信息）
     */
    @Override
    public PageDTO<UserVO> queryUserPage(UserPageQuery query) {
        //1. 构建分页对象：默认按创建时间倒序
        Page<User> page = query.toMpPageDefaultSortByCreateTimeDesc();
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        //2. 组装筛选条件：userType 精确匹配 + keyword 对三字段模糊匹配（或关系）
        if (query.getUserType() != null) {
            wrapper.eq(User::getUserType, query.getUserType());
        }
        if (StrUtil.isNotBlank(query.getKeyword())) {
            String keyword = query.getKeyword().trim();
            wrapper.and(w -> w.like(User::getNickname, keyword)
                    .or().like(User::getPhone, keyword)
                    .or().like(User::getEmail, keyword));
        }
        //3. 分页查询并把记录转列表 VO，封装成分页响应
        userMapper.selectPage(page, wrapper);
        List<UserVO> vos = BeanUtils.copyList(page.getRecords(), UserVO.class);
        // 注意：PageDTO.of(Page, List) 要求两者泛型一致，Page<User> 与 List<UserVO> 类型不同，
        // 故使用其内部等价实现 of(Long, List)（of(Page, List) 本质就是 of(page.getTotal(), list)）
        return PageDTO.of(page.getTotal(), vos);
    }

    @Override
    public long countTodayUsers() {
        //1. 统计今日新增用户：create_time 落在今日 0 点之后（含），供数据中心聚合
        return userMapper.selectCount(new LambdaQueryWrapper<User>()
                .ge(User::getCreateTime, LocalDate.now().atStartOfDay()));
    }

    /**
     * 教师扩展表更新（事务内调用）
     *
     * 存在则 update、不存在则 insert：教师资料首次编辑时扩展表尚无记录，需补插；
     * 空字符串统一归一为 null，避免把空白串写入库。
     *
     * @param userId 用户档案 id（关联 user.id）
     * @param dto    资料入参（college / title / bio 为教师扩展字段）
     */
    private void updateTeacherInfo(Long userId, UserProfileFormDTO dto) {
        //1. 按 userId 查教师扩展档案：判断走更新还是补插
        TeacherInfo teacherInfo = teacherInfoMapper.selectOne(new LambdaQueryWrapper<TeacherInfo>()
                .eq(TeacherInfo::getUserId, userId));
        if (teacherInfo != null) {
            //2. 已建档：整体拷贝入参后仅更新有值的扩展字段，保留原 id 定位记录
            TeacherInfo update = BeanUtils.copyBean(dto, TeacherInfo.class);
            update.setId(teacherInfo.getId());
            if (update.getCollege() != null || update.getTitle() != null || update.getBio() != null) {
                teacherInfoMapper.updateById(update);
            }
        } else {
            //3. 未建档：补插一行，userId 关联主表，空字符串归一为 null
            TeacherInfo insert = new TeacherInfo();
            insert.setUserId(userId);
            insert.setCollege(StrUtil.emptyToNull(dto.getCollege()));
            insert.setTitle(StrUtil.emptyToNull(dto.getTitle()));
            insert.setBio(StrUtil.emptyToNull(dto.getBio()));
            teacherInfoMapper.insert(insert);
        }
    }

    /**
     * 学生扩展表更新（事务内调用）
     *
     * 存在则 update、不存在则 insert：学生资料首次编辑时扩展表尚无记录，需补插；
     * 空字符串统一归一为 null，避免把空白串写入库。
     *
     * @param userId 用户档案 id（关联 user.id）
     * @param dto    资料入参（studentNo / major / grade / className 为学生扩展字段）
     */
    private void updateStudentInfo(Long userId, UserProfileFormDTO dto) {
        //1. 按 userId 查学生扩展档案：判断走更新还是补插
        StudentInfo studentInfo = studentInfoMapper.selectOne(new LambdaQueryWrapper<StudentInfo>()
                .eq(StudentInfo::getUserId, userId));
        if (studentInfo != null) {
            //2. 已建档：整体拷贝入参后仅更新有值的扩展字段，保留原 id 定位记录
            StudentInfo update = BeanUtils.copyBean(dto, StudentInfo.class);
            update.setId(studentInfo.getId());
            if (update.getStudentNo() != null || update.getMajor() != null
                    || update.getGrade() != null || update.getClassName() != null) {
                studentInfoMapper.updateById(update);
            }
        } else {
            //3. 未建档：补插一行，userId 关联主表，空字符串归一为 null
            StudentInfo insert = new StudentInfo();
            insert.setUserId(userId);
            insert.setStudentNo(StrUtil.emptyToNull(dto.getStudentNo()));
            insert.setMajor(StrUtil.emptyToNull(dto.getMajor()));
            insert.setGrade(StrUtil.emptyToNull(dto.getGrade()));
            insert.setClassName(StrUtil.emptyToNull(dto.getClassName()));
            studentInfoMapper.insert(insert);
        }
    }

    /**
     * 组装用户详情 VO
     *
     * 主表字段 copyBean 到 VO 后，按 userType 查对应扩展表并填充扩展字段；
     * 扩展表无记录时扩展字段保持 null（兼容早期未建档扩展信息的用户）。
     *
     * @param user 已查询到的用户档案
     * @return 聚合 user 主表 + 对应扩展表字段的详情 VO
     */
    private UserDetailVO toDetailVO(User user) {
        //1. 主表字段拷贝到 VO：id / accountId / 基础资料等公共字段
        UserDetailVO vo = BeanUtils.copyBean(user, UserDetailVO.class);
        Integer userType = user.getUserType();
        //2. 按用户类型补扩展字段：教师填充 teacher_info、学生填充 student_info
        if (userType != null && userType == UserType.TEACHER.getValue()) {
            TeacherInfo info = teacherInfoMapper.selectOne(new LambdaQueryWrapper<TeacherInfo>()
                    .eq(TeacherInfo::getUserId, user.getId()));
            if (info != null) {
                vo.setCollege(info.getCollege());
                vo.setTitle(info.getTitle());
                vo.setBio(info.getBio());
            }
        } else if (userType != null && userType == UserType.STUDENT.getValue()) {
            StudentInfo info = studentInfoMapper.selectOne(new LambdaQueryWrapper<StudentInfo>()
                    .eq(StudentInfo::getUserId, user.getId()));
            if (info != null) {
                vo.setStudentNo(info.getStudentNo());
                vo.setMajor(info.getMajor());
                vo.setGrade(info.getGrade());
                vo.setClassName(info.getClassName());
            }
        }
        return vo;
    }
}
