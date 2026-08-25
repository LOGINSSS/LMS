package com.lms.common.enums;

import com.lms.common.constants.ErrorInfo;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 通用错误码（全局唯一；业务错误码在各模块 constants 中扩展）
 */
@Getter
@AllArgsConstructor
public enum CommonError implements ErrorInfo {

    BAD_REQUEST(400, "请求参数错误"),
    UNAUTHORIZED(401, "未登录或登录已过期"),
    FORBIDDEN(403, "无权限访问"),
    NOT_FOUND(404, "请求资源不存在"),
    BIZ_ILLEGAL(400, "业务操作非法"),
    DB_ERROR(500, "数据库操作失败"),
    SERVER_ERROR(500, "系统繁忙，请稍后再试");

    private final int code;
    private final String msg;
}
