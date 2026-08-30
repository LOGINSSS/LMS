package com.lms.ai.context;

/**
 * Agent 工具执行线程的用户上下文桥（spec §5.3：任务上下文贯穿互调）
 *
 * Agent 工具方法体内把 RuntimeContext 中的用户身份（lmsUserId/lmsUserType）桥接到
 * 当前线程，供 Feign RequestInterceptor 拼 user-info 头转发给业务模块
 * （业务模块 UserInfoInterceptor 解析后写入 UserContext）。
 * 注意：reactor 执行链可能切换线程，因此工具方法必须 try-finally 清理。
 */
public final class AgentContextHolder {

    private static final ThreadLocal<Long> TL_USER_ID = new ThreadLocal<>();
    private static final ThreadLocal<Integer> TL_USER_TYPE = new ThreadLocal<>();

    private AgentContextHolder() {
    }

    public static void set(Long userId, Integer userType) {
        TL_USER_ID.set(userId);
        TL_USER_TYPE.set(userType);
    }

    public static Long getUserId() {
        return TL_USER_ID.get();
    }

    public static Integer getUserType() {
        return TL_USER_TYPE.get();
    }

    public static void clear() {
        TL_USER_ID.remove();
        TL_USER_TYPE.remove();
    }
}
