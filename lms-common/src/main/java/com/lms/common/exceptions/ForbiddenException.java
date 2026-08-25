package com.lms.common.exceptions;

import com.lms.common.constants.ErrorInfo;
import com.lms.common.enums.CommonError;

/**
 * 无权限访问（403）
 */
public class ForbiddenException extends CommonException {

    public ForbiddenException(String message) {
        super(CommonError.FORBIDDEN.getCode(), message);
    }

    public ForbiddenException(ErrorInfo errorInfo) {
        super(errorInfo);
    }
}
