package com.lms.course.course.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

/**
 * 用户简要信息（跨服务传输对象）
 *
 * 用途：lms-course 调用 lms-user 查询教师档案时只关心 id / 昵称 / 类型，
 * 声明最小字段集，未知字段忽略。
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class UserSimpleDTO {

    /** 用户档案 id（与 lms_user.user.id 对应） */
    private Long id;

    /** 昵称（课程卡片展示教师名用） */
    private String nickname;

    /** 用户类型（1 学生 / 2 教师） */
    private Integer userType;
}
