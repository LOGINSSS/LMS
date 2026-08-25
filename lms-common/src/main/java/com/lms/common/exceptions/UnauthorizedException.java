package com.lms.common.exceptions;

import com.lms.common.constants.ErrorInfo;
import com.lms.common.enums.CommonError;

/**
 * 未登录或登录过期（401）
 */
public class UnauthorizedException extends CommonException {

    public UnauthorizedException(String message) {
        super(CommonError.UNAUTHORIZED.getCode(), message);
    }

    public UnauthorizedException(ErrorInfo errorInfo) {
        super(errorInfo);
    }
}
