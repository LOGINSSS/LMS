package com.lms.common.utils;

/**
 * 用户上下文：ThreadLocal 存储当前登录用户 id，一次请求内随处可取
 * <p>写入：登录拦截器（解析用户头）；清理：拦截器 afterCompletion（防串号/内存泄漏）
 * <p>注意：异步线程不传递 ThreadLocal，需要时手动传参或换 TransmittableThreadLocal
 */
public class UserContext {

    private static final ThreadLocal<Long> TL = new ThreadLocal<>();

    /** 保存当前用户 id */
    public static void setUser(Long userId) {
        TL.set(userId);
    }

    /** 获取当前用户 id */
    public static Long getUser() {
        return TL.get();
    }

    /** 请求结束清理 */
    public static void removeUser() {
        TL.remove();
    }
}
