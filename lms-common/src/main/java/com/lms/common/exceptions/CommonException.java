package com.lms.common.exceptions;

import com.lms.common.constants.ErrorInfo;
import com.lms.common.enums.CommonError;
import lombok.Getter;

/**
 * 公共异常基类：所有业务异常继承此类，由全局异常处理器统一转成 R
 */
@Getter
public class CommonException extends RuntimeException {

    private final int code;

    public CommonException(String message) {
        super(message);
        this.code = CommonError.SERVER_ERROR.getCode();
    }

    public CommonException(int code, String message) {
        super(message);
        this.code = code;
    }

    public CommonException(ErrorInfo errorInfo) {
        super(errorInfo.getMsg());
        this.code = errorInfo.getCode();
    }
}
