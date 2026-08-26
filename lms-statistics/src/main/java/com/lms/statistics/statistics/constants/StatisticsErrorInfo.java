package com.lms.statistics.statistics.constants;

import com.lms.common.constants.ErrorInfo;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 数据中心服务错误码（业务错误码 2801 起，全局唯一）
 */
@Getter
@AllArgsConstructor
public enum StatisticsErrorInfo implements ErrorInfo {

    /** 跨服务聚合统计整体失败时抛出 */
    STATS_AGGREGATE_FAILED(2801, "统计数据聚合失败");

    private final int code;
    private final String msg;
}
