package com.lms.media.media.constants;

import com.lms.common.constants.ErrorInfo;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 媒资服务错误码（业务错误码 2301 起，全局唯一）
 */
@Getter
@AllArgsConstructor
public enum MediaErrorInfo implements ErrorInfo {

    /** 文件写入存储失败（磁盘 IO 异常等） */
    UPLOAD_FAILED(2301, "文件上传失败"),

    /** 按 id 查询或删除不存在的媒资时抛出 */
    MEDIA_NOT_FOUND(2302, "媒资不存在"),

    /** 上传文件超过大小上限（100MB） */
    FILE_TOO_LARGE(2303, "文件大小超出限制"),

    /** 文件扩展名缺失或不在支持范围内 */
    UNSUPPORTED_TYPE(2304, "不支持的文件类型");

    private final int code;
    private final String msg;
}
