package com.lms.common.exceptions;

import com.lms.common.constants.ErrorInfo;
import com.lms.common.enums.CommonError;

/**
 * 请求参数错误（400）
 */
public class BadRequestException extends CommonException {

    public BadRequestException(String message) {
        super(CommonError.BAD_REQUEST.getCode(), message);
    }

    public BadRequestException(ErrorInfo errorInfo) {
        super(errorInfo);
    }
}
