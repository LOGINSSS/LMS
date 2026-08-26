package com.lms.exam.exam.enums;

import com.lms.common.enums.BaseEnum;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 题型枚举
 *
 * 业务含义：题目作答方式，单选/多选/判断；落库字段 question.type（TINYINT）。
 */
@Getter
@AllArgsConstructor
public enum QuestionType implements BaseEnum {

    /** 单选题：答案为一个选项 */
    SINGLE(1, "单选"),

    /** 多选题：答案为多个选项 */
    MULTIPLE(2, "多选"),

    /** 判断题：答案为对/错 */
    JUDGE(3, "判断");

    private final int value;
    private final String desc;

    public static QuestionType of(int value) {
        for (QuestionType type : values()) {
            if (type.value == value) {
                return type;
            }
        }
        return null;
    }
}
