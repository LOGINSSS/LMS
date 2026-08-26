package com.lms.learning.learning.constants;

import com.lms.common.constants.ErrorInfo;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 学习过程服务错误码（业务错误码 2701 起，全局唯一）
 */
@Getter
@AllArgsConstructor
public enum LearningErrorInfo implements ErrorInfo {

    /** 按 id 查询/操作不存在的课次时抛出 */
    LESSON_NOT_FOUND(2701, "课次不存在"),

    /** 按 id 查询/操作不存在的笔记时抛出 */
    NOTE_NOT_FOUND(2702, "笔记不存在"),

    /** 按 id 查询/操作不存在的问答问题时抛出 */
    QUESTION_NOT_FOUND(2703, "问题不存在"),

    /** 同一天重复签到时抛出 */
    ALREADY_SIGNED(2704, "今日已签到"),

    /** 积分流水写入失败时抛出 */
    POINTS_SAVE_FAILED(2705, "积分记录失败");

    private final int code;
    private final String msg;
}
