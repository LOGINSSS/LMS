package com.lms.remark.remark.enums;

import com.lms.common.enums.BaseEnum;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 点赞对象类型枚举
 *
 * 业务含义：点赞记录跨业务对象复用，biz_type 标识对象归属，
 * 1 课程 / 2 笔记 / 3 问答，后续新对象类型在此追加。
 * 落库字段：liked_record.biz_type（TINYINT）。
 */
@Getter
@AllArgsConstructor
public enum BizType implements BaseEnum {

    /** 课程 */
    COURSE(1, "课程"),

    /** 笔记 */
    NOTE(2, "笔记"),

    /** 问答 */
    QUESTION(3, "问答");

    private final int value;
    private final String desc;

    /**
     * 按枚举值查找，未知值返回 null（由调用方决定是否抛业务异常）
     *
     * @param value 枚举值
     * @return 匹配的 BizType，未匹配返回 null
     */
    public static BizType of(int value) {
        for (BizType type : values()) {
            if (type.value == value) {
                return type;
            }
        }
        return null;
    }
}
