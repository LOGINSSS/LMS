package com.lms.course.course.enums;

import com.lms.common.enums.BaseEnum;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 选课状态枚举
 *
 * 业务含义：记录学生对某课程的选课关系是否有效，退课采用状态置 0（保留历史记录）。
 * 落库字段：course_enrollment.status（TINYINT）。
 */
@Getter
@AllArgsConstructor
public enum EnrollmentStatus implements BaseEnum {

    /** 已退课 */
    QUIT(0, "已退课"),

    /** 选课中 */
    ACTIVE(1, "选课中");

    private final int value;
    private final String desc;
}
