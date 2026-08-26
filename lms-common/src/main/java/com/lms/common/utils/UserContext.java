package com.lms.common.utils;

/**
 * 用户上下文（ThreadLocal 工具）
 *
 * 用途：一次请求内随处获取当前登录用户 id 与类型，避免在 controller → service → mapper
 * 整条链路逐层传参。
 *
 * 适用场景：
 *   1. 业务服务中取当前登录用户：getUser() / getUserType()
 *   2. 需要判断角色（学生/教师）时取 getUserType()，与 UserType 枚举比对
 *
 * 写入来源：UserInfoInterceptor 解析网关透传的 user-info 头后写入；
 * 请求结束由拦截器 afterCompletion 调用 removeUser() 清理，防止连接池复用导致串号。
 * 注意：异步线程不传递 ThreadLocal，需手动传参或换 TransmittableThreadLocal。
 */
public class UserContext {

    private static final ThreadLocal<Long> TL_USER = new ThreadLocal<>();

    private static final ThreadLocal<Integer> TL_USER_TYPE = new ThreadLocal<>();

    /** 保存当前用户 id */
    public static void setUser(Long userId) {
        TL_USER.set(userId);
    }

    /** 获取当前用户 id */
    public static Long getUser() {
        return TL_USER.get();
    }

    /** 保存当前用户类型（1 学生 / 2 教师） */
    public static void setUserType(Integer userType) {
        TL_USER_TYPE.set(userType);
    }

    /** 获取当前用户类型（1 学生 / 2 教师） */
    public static Integer getUserType() {
        return TL_USER_TYPE.get();
    }

    /** 请求结束清理全部 */
    public static void removeUser() {
        TL_USER.remove();
        TL_USER_TYPE.remove();
    }
}
