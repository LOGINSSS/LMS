package com.lms.exam.exam.enums;

import com.lms.common.enums.BaseEnum;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 题目-业务绑定类型枚举（0.2 课程内容域扩展）
 *
 * 业务含义：question_biz 绑定的业务对象类型——
 * 1 课程（课程级测验）/ 2 章节（章节练习）/ 3 考试卷（0.3 组卷预留）。
 * 落库字段：question_biz.biz_type（TINYINT）。
 */
@Getter
@AllArgsConstructor
public enum BizType implements BaseEnum {

    /** 课程：biz_id = course.id */
    COURSE(1, "课程"),

    /** 章节：biz_id = course_catalog.id（章节练习） */
    CHAPTER(2, "章节"),

    /** 考试卷：biz_id = 试卷 id（0.3 组卷预留） */
    PAPER(3, "考试卷");

    private final int value;
    private final String desc;

    public static BizType of(int value) {
        for (BizType t : values()) {
            if (t.value == value) {
                return t;
            }
        }
        return null;
    }
}
