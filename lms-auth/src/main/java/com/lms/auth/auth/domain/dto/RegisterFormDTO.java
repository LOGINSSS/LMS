package com.lms.auth.auth.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 注册表单 DTO（学生/教师共用）
 *
 * 场景：POST /auth/register 的请求体，承载注册凭据与用户资料。
 *
 * 业务规则：
 * - 用户名 3-50 位；密码 6-32 位；userType 必填（1 学生 2 教师）
 * - 扩展字段按用户类型透传给 lms-user 创建档案（教师：college/title/bio；学生：studentNo/major/grade/className）
 */
@Data
public class RegisterFormDTO {

    /** 登录账号 */
    @NotBlank(message = "请输入用户名")
    @Size(min = 3, max = 50, message = "用户名长度需在 3-50 位之间")
    private String username;

    /** 密码（明文，服务端 BCrypt 加密存储） */
    @NotBlank(message = "请输入密码")
    @Size(min = 6, max = 32, message = "密码长度需在 6-32 位之间")
    private String password;

    /** 用户类型：1 学生 2 教师 */
    @NotNull(message = "请选择用户类型")
    private Integer userType;

    /** 昵称 */
    private String nickname;

    /** 手机号 */
    private String phone;

    /** 邮箱 */
    private String email;

    /** 学院（教师） */
    private String college;

    /** 职称（教师） */
    private String title;

    /** 简介（教师） */
    private String bio;

    /** 学号（学生） */
    private String studentNo;

    /** 专业（学生） */
    private String major;

    /** 年级（学生） */
    private String grade;

    /** 班级（学生） */
    private String className;
}
