package com.lms.common.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 用户类型枚举
 *
 * 业务含义：登录、档案、JWT、网关透传全链路共享的类型约定，区分学生端与教师端。
 * 取值：1 学生 / 2 教师，入库为 TINYINT，出参为 int。
 */
@Getter
@AllArgsConstructor
public enum UserType implements BaseEnum {

    /** 学生：可注册课程、学习、提交作业 */
    STUDENT(1, "学生"),
    /** 教师：可创建课程、批改作业、管理教学班 */
    TEACHER(2, "教师");

    private final int value;
    private final String desc;

    public static UserType of(int value) {
        for (UserType type : values()) {
            if (type.value == value) {
                return type;
            }
        }
        return null;
    }
}
