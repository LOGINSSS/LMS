package com.lms.grab.grab.constants;

import com.lms.common.constants.ErrorInfo;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 抢课服务错误码（业务错误码 2601 起，全局唯一）
 */
@Getter
@AllArgsConstructor
public enum GrabErrorInfo implements ErrorInfo {

    /** 课程不存在 */
    COURSE_NOT_FOUND(2601, "课程不存在"),

    /** 抢课未开始或已结束 */
    GRAB_NOT_OPEN(2602, "抢课未开始或已结束"),

    /** 库存不足 */
    GRAB_SOLD_OUT(2603, "课程名额已抢完"),

    /** 已抢过该课程 */
    GRAB_DUPLICATED(2604, "已抢过该课程"),

    /** 抢课窗口未预热（发布时未调用 prepare） */
    GRAB_NOT_PREPARED(2605, "抢课窗口尚未开放"),

    /** 抢课记录落库失败 */
    GRAB_SAVE_FAILED(2606, "抢课记录保存失败");

    private final int code;
    private final String msg;
}
