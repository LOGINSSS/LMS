package com.lms.auth.auth.client.dto;

import lombok.Data;

/**
 * 用户档案 DTO：Feign 调用 lms-user 创建档案的请求体
 *
 * 业务含义：承载注册时采集的用户资料，经 UserClient 透传给 lms-user 服务创建档案。
 *
 * 注意点：
 * - 字段结构与 lms-user 服务端约定一致，勿改名
 * - 扩展字段按用户类型透传：教师（college/title/bio）、学生（studentNo/major/grade/className）
 */
@Data
public class UserProfileDTO {

    /** 登录账号 id（lms_auth.account.id，lms-user 侧反查账号用） */
    private Long accountId;

    /** 用户类型：1 学生 2 教师 */
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
