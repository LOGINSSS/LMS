package com.lms.common.exceptions;

import com.lms.common.enums.CommonError;

/**
 * 业务操作非法（如状态不匹配、重复操作）
 */
public class BizIllegalException extends CommonException {

    public BizIllegalException(String message) {
        super(CommonError.BIZ_ILLEGAL.getCode(), message);
    }
}
