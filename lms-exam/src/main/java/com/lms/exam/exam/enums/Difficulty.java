package com.lms.exam.exam.enums;

import com.lms.common.enums.BaseEnum;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 题目难度枚举
 *
 * 业务含义：题目难度分级，用于组卷与筛选；落库字段 question.difficulty（TINYINT）。
 */
@Getter
@AllArgsConstructor
public enum Difficulty implements BaseEnum {

    /** 简单 */
    EASY(1, "简单"),

    /** 中等 */
    MIDDLE(2, "中等"),

    /** 困难 */
    HARD(3, "困难");

    private final int value;
    private final String desc;

    public static Difficulty of(int value) {
        for (Difficulty d : values()) {
            if (d.value == value) {
                return d;
            }
        }
        return null;
    }
}
