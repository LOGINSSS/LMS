package com.lms.user.user.service;

import com.lms.common.domain.dto.PageDTO;
import com.lms.user.user.domain.dto.UserDetailVO;
import com.lms.user.user.domain.dto.UserProfileFormDTO;
import com.lms.user.user.domain.dto.UserVO;
import com.lms.user.user.domain.query.UserPageQuery;

/**
 * 用户档案服务接口
 *
 * 职责：档案创建（认证服务内部 Feign 调用）、本人 / 按 id 详情查询、
 * 本人资料修改、管理端分页查询，是 lms-user 模块的核心业务入口。
 */
public interface IUserService {

    /**
     * 创建用户档案（幂等）
     *
     * 业务背景：认证服务注册成功后通过 Feign 调用本接口建档，网络重试可能重复触发，
     * 因此按 accountId 先查后插，已建档直接返回已有 id，防止重复档案。
     *
     * @param dto 档案入参（accountId / userType 创建场景必填，扩展字段可选）
     * @return 用户档案 id（新建或已存在）
     * @throws CommonException 参数缺失、用户类型非法或插入失败时抛出
     */
    Long createUserProfile(UserProfileFormDTO dto);

    /**
     * 当前登录用户详情
     *
     * 业务背景：本人中心页数据来源，用户 id 不靠入参传递，
     * 由公共拦截器解析网关透传的 user-info 头写入 UserContext，本方法直接读取。
     *
     * @return 当前用户详情（user 主表 + 对应扩展表字段聚合）
     * @throws CommonException 未登录或档案不存在时抛出
     */
    UserDetailVO getMyDetail();

    /**
     * 修改当前用户资料（仅更新非 null 字段，含对应扩展表）
     *
     * 业务背景：本人资料编辑页提交，昵称 / 头像 / 手机号 / 邮箱更新到 user 主表，
     * 教师 / 学生扩展字段按 userType 更新到对应扩展表；扩展表不存在时自动补插。
     *
     * @param dto 资料入参（仅非 null 字段参与更新，扩展字段空字符串归一为 null 不落库）
     * @throws CommonException 未登录、档案不存在或主表更新失败时抛出
     */
    void updateMyProfile(UserProfileFormDTO dto);

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
    UserDetailVO getUserDetail(Long id);

    /**
     * 管理端分页查询用户（可按用户类型筛选 + 关键字模糊匹配）
     *
     * 业务背景：管理后台用户列表，支持按学生 / 教师类型精确筛选，
     * 以及按昵称 / 手机号 / 邮箱任一关键字模糊搜索，默认按创建时间倒序。
     *
     * @param query 分页与筛选条件（继承 PageQuery）
     * @return 分页结果（列表项 UserVO 仅含主表公共字段）
     */
    PageDTO<UserVO> queryUserPage(UserPageQuery query);

    /**
     * 今日新增用户数（数据中心看板用）
     *
     * 业务背景：统计口径为 user 主表 create_time 落在今日 0 点之后（含）的档案数，
     * 供 lms-statistics 聚合"今日数据"。
     *
     * @return 今日新增用户数
     */
    long countTodayUsers();
}
