package com.lms.user.user.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户详情 VO
 *
 * 使用场景：本人详情（GET /users/me）与按 id 查询（GET /users/{id}）的返回体，
 * 面向本人与认证服务；聚合 user 主表公共字段 + 对应扩展表字段（教师或学生）。
 */
@Data
@Schema(description = "用户详情")
public class UserDetailVO {

    /** 用户档案 id（user 主表主键） */
    @Schema(description = "用户档案id")
    private Long id;

    /** 账号 id：关联 lms_auth.account.id */
    @Schema(description = "账号id")
    private Long accountId;

    /** 用户类型：1 学生 / 2 教师，取值见 UserType 枚举 */
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

    /** 状态：1 正常 / 0 禁用 */
    @Schema(description = "状态：1正常 0禁用")
    private Integer status;

    /** 创建时间 */
    @Schema(description = "创建时间")
    private LocalDateTime createTime;

    /** 院系（教师扩展字段，来自 teacher_info） */
    @Schema(description = "院系")
    private String college;

    /** 职称（教师扩展字段，来自 teacher_info） */
    @Schema(description = "职称")
    private String title;

    /** 个人简介（教师扩展字段，来自 teacher_info） */
    @Schema(description = "个人简介")
    private String bio;

    /** 学号（学生扩展字段，来自 student_info） */
    @Schema(description = "学号")
    private String studentNo;

    /** 专业（学生扩展字段，来自 student_info） */
    @Schema(description = "专业")
    private String major;

    /** 年级（学生扩展字段，来自 student_info） */
    @Schema(description = "年级")
    private String grade;

    /** 班级（学生扩展字段，来自 student_info） */
    @Schema(description = "班级")
    private String className;
}
