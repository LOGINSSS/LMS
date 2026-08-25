package com.lms.common.exceptions;

import com.lms.common.enums.CommonError;

/**
 * 数据库操作异常
 */
public class DbException extends CommonException {

    public DbException(String message) {
        super(CommonError.DB_ERROR.getCode(), message);
    }

    public DbException(String message, Throwable cause) {
        super(CommonError.DB_ERROR.getCode(), message);
        initCause(cause);
    }
}
