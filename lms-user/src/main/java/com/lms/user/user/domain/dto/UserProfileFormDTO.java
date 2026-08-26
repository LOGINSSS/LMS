package com.lms.user.user.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 用户档案表单 DTO（创建 / 修改共用入参）
 *
 * 使用场景：创建档案（POST /users，认证服务内部 Feign 调用）与修改资料（PUT /users/me）共用；
 * 字段结构与 lms-auth 的 UserProfileDTO JSON 保持一致，勿随意改名。
 *
 * 校验规则：accountId / userType 仅创建场景必填、修改场景可空，故不做 @NotNull 注解，
 * 由 Service 按场景断言（见 UserServiceImpl.createUserProfile）。
 */
@Data
@Schema(description = "用户档案表单")
public class UserProfileFormDTO {

    /** 账号 id：关联 lms_auth.account.id，创建场景必填（幂等键）、修改场景为空不更新 */
    @Schema(description = "账号id")
    private Long accountId;

    /** 用户类型：1 学生 / 2 教师，取值见 UserType 枚举，创建场景必填 */
    @Schema(description = "用户类型：1学生 2教师")
    private Integer userType;

    /** 昵称 */
    @Schema(description = "昵称")
    private String nickname;

    /** 头像 URL */
    @Schema(description = "头像URL")
    private String avatar;

    /** 手机号 */
    @Schema(description = "手机号")
    private String phone;

    /** 邮箱 */
    @Schema(description = "邮箱")
    private String email;

    /** 院系（教师扩展字段，写入 teacher_info） */
    @Schema(description = "院系")
    private String college;

    /** 职称（教师扩展字段，写入 teacher_info） */
    @Schema(description = "职称")
    private String title;

    /** 个人简介（教师扩展字段，写入 teacher_info） */
    @Schema(description = "个人简介")
    private String bio;

    /** 学号（学生扩展字段，写入 student_info） */
    @Schema(description = "学号")
    private String studentNo;

    /** 专业（学生扩展字段，写入 student_info） */
    @Schema(description = "专业")
    private String major;

    /** 年级（学生扩展字段，写入 student_info） */
    @Schema(description = "年级")
    private String grade;

    /** 班级（学生扩展字段，写入 student_info） */
    @Schema(description = "班级")
    private String className;
}
