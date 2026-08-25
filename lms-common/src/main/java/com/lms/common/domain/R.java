package com.lms.common.domain;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.lms.common.constants.ErrorInfo;
import com.lms.common.enums.CommonError;
import lombok.Data;

/**
 * 统一响应体
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class R<T> {

    public static final int SUCCESS_CODE = 1;

    /** 状态码：1 成功，其他为错误码 */
    private int code;
    /** 提示信息 */
    private String msg;
    /** 业务数据 */
    private T data;

    public static <T> R<T> ok() {
        return build(SUCCESS_CODE, "OK", null);
    }

    public static <T> R<T> ok(T data) {
        return build(SUCCESS_CODE, "OK", data);
    }

    public static <T> R<T> fail(int code, String msg) {
        return build(code, msg, null);
    }

    public static <T> R<T> fail(ErrorInfo errorInfo) {
        return build(errorInfo.getCode(), errorInfo.getMsg(), null);
    }

    public static <T> R<T> fail(String msg) {
        return build(CommonError.SERVER_ERROR.getCode(), msg, null);
    }

    private static <T> R<T> build(int code, String msg, T data) {
        R<T> r = new R<>();
        r.setCode(code);
        r.setMsg(msg);
        r.setData(data);
        return r;
    }

    public boolean success() {
        return code == SUCCESS_CODE;
    }
}
