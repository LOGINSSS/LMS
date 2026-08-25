package com.lms.common.constants;

/**
 * 错误码接口：所有错误码实现此接口，保证全局唯一
 */
public interface ErrorInfo {

    /** 错误码 */
    int getCode();

    /** 错误文案 */
    String getMsg();
}
