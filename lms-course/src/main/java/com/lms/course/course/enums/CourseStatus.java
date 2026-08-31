package com.lms.course.course.enums;

import com.lms.common.enums.BaseEnum;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 课程状态枚举（0.2 发布状态机）
 *
 * 业务含义：控制课程的生命周期与对学生的可见性。
 * 0 草稿（老师编辑中，不可见）→ 1 待发布（已提交抢课窗口，未到开始时间）→
 * 2 抢课中（grab_start_time ≤ now ≤ grab_end_time，卡片可见、可抢）→
 * 3 进行中（抢课结束，已拥有者可学习）→ 4 已结束；任意态可 5 下架。
 * 落库字段：course.status（TINYINT）。
 */
@Getter
@AllArgsConstructor
public enum CourseStatus implements BaseEnum {

    /** 草稿：老师编辑中，学生不可见 */
    DRAFT(0, "草稿"),

    /** 待发布：已提交抢课窗口，未到开始时间 */
    PENDING_GRAB(1, "待发布"),

    /** 抢课中：卡片可见，学生可抢课 */
    GRABBING(2, "抢课中"),

    /** 进行中：抢课结束，已拥有者可学习 */
    ONGOING(3, "进行中"),

    /** 已结束 */
    FINISHED(4, "已结束"),

    /** 已下架：学生不可见、不可选课 */
    OFFLINE(5, "已下架");

    private final int value;
    private final String desc;

    public static CourseStatus of(int value) {
        for (CourseStatus s : values()) {
            if (s.value == value) {
                return s;
            }
        }
        return null;
    }
}
