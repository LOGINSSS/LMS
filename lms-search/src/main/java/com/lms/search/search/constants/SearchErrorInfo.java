package com.lms.search.search.constants;

import com.lms.common.constants.ErrorInfo;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 搜索服务错误码（业务错误码 2601 起，全局唯一）
 */
@Getter
@AllArgsConstructor
public enum SearchErrorInfo implements ErrorInfo {

    /** 上报的兴趣标签为空或超长时抛出 */
    TAG_INVALID(2601, "兴趣标签不合法"),

    /** ES 索引同步失败时抛出 */
    SYNC_FAILED(2602, "课程索引同步失败");

    private final int code;
    private final String msg;
}
