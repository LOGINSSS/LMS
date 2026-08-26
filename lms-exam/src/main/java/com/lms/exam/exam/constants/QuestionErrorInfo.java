package com.lms.exam.exam.constants;

import com.lms.common.constants.ErrorInfo;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 题库服务错误码（业务错误码 2501 起，全局唯一）
 */
@Getter
@AllArgsConstructor
public enum QuestionErrorInfo implements ErrorInfo {

    /** 按 id 查询/操作不存在的题目时抛出 */
    QUESTION_NOT_FOUND(2501, "题目不存在"),

    /** 题目插入/更新落库失败 */
    QUESTION_SAVE_FAILED(2502, "题目保存失败"),

    /** 题目与业务绑定关系落库失败 */
    BIZ_BIND_FAILED(2503, "题目绑定业务失败");

    private final int code;
    private final String msg;
}
