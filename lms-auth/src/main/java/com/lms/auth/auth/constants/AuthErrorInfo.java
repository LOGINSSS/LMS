package com.lms.auth.auth.constants;

import com.lms.common.constants.ErrorInfo;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 认证服务业务错误码枚举（2001-2999 段，全局唯一）
 *
 * 业务含义：集中定义认证服务的业务异常码与提示文案，写法与 lms-common 的 ErrorInfo 约定对齐，
 * 由全局异常处理器统一转换为响应返回给前端。
 */
@Getter
@AllArgsConstructor
public enum AuthErrorInfo implements ErrorInfo {

    /** 注册：用户名已存在（触发场景：注册时用户名已被占用） */
    ACCOUNT_EXISTS(2001, "该账号已注册"),

    /** 登录：用户名或密码错误（触发场景：账号不存在或密码校验失败，统一文案防账号枚举） */
    LOGIN_FAILED(2002, "用户名或密码错误"),

    /** 登录：账号被禁用（触发场景：账号状态非正常时拒绝登录） */
    ACCOUNT_DISABLED(2003, "账号已被禁用，请联系管理员"),

    /** token 无效（触发场景：登出 / me 时 token 缺失、格式错误、验签失败或已过期） */
    TOKEN_INVALID(2004, "登录已过期，请重新登录"),

    /** 注册失败（触发场景：Feign 调用 lms-user 创建档案失败降级抛出） */
    REGISTER_FAILED(2005, "注册失败，请稍后重试");

    /** 错误码（HTTP 响应与日志中标识具体业务错误） */
    private final int code;

    /** 错误提示文案（返回给前端展示） */
    private final String msg;
}
