package com.lms.course.course.enums;

import com.lms.common.enums.BaseEnum;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 课程状态枚举
 *
 * 业务含义：控制课程对学生的可见性，卡片列表只展示已发布课程。
 * 落库字段：course.status（TINYINT）。
 */
@Getter
@AllArgsConstructor
public enum CourseStatus implements BaseEnum {

    /** 已下架：学生不可见、不可选课 */
    OFFLINE(0, "已下架"),

    /** 已发布：学生可见、可选课 */
    PUBLISHED(1, "已发布");

    private final int value;
    private final String desc;
}
