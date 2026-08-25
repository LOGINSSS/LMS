package com.lms.common.autoconfigure.mvc;

import com.lms.common.domain.R;
import com.lms.common.enums.CommonError;
import com.lms.common.exceptions.CommonException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 全局异常处理：统一包装为 R 返回
 */
@Slf4j
@RestControllerAdvice
public class CommonExceptionAdvice {

    /** 业务异常（CommonException 及所有子类） */
    @ExceptionHandler(CommonException.class)
    public R<Void> handleCommonException(CommonException e) {
        log.warn("业务异常: {}", e.getMessage());
        return R.fail(e.getCode(), e.getMessage());
    }

    /** @RequestBody 参数校验失败 */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public R<Void> handleMethodArgumentNotValid(MethodArgumentNotValidException e) {
        String msg = firstFieldError(e.getBindingResult().getFieldErrors());
        return R.fail(CommonError.BAD_REQUEST.getCode(), msg);
    }

    /** 表单绑定参数校验失败 */
    @ExceptionHandler(BindException.class)
    public R<Void> handleBind(BindException e) {
        String msg = firstFieldError(e.getBindingResult().getFieldErrors());
        return R.fail(CommonError.BAD_REQUEST.getCode(), msg);
    }

    /** 请求体格式错误（JSON 解析失败） */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public R<Void> handleNotReadable(HttpMessageNotReadableException e) {
        log.warn("请求体解析失败: {}", e.getMessage());
        return R.fail(CommonError.BAD_REQUEST.getCode(), "请求体格式错误");
    }

    /** 兜底异常 */
    @ExceptionHandler(Exception.class)
    public R<Void> handleException(Exception e) {
        log.error("系统异常", e);
        return R.fail(CommonError.SERVER_ERROR);
    }

    private String firstFieldError(java.util.List<FieldError> fieldErrors) {
        return fieldErrors.stream()
                .map(FieldError::getDefaultMessage)
                .findFirst()
                .orElse(CommonError.BAD_REQUEST.getMsg());
    }
}
