package com.lms.exam.config;

/**
 * 异步线程用户桥（考试 Kafka 消费端用）
 *
 * 消费线程没有 HTTP 用户上下文（UserContext 为空），Feign 回流 lms-learning 时
 * 由 UserInfoFeignConfig 回退读取本 holder 拼 user-info 头，使作答记录以学生身份归属。
 */
public final class AsyncUser {

    private static final ThreadLocal<Long> USER_ID = new ThreadLocal<>();
    private static final ThreadLocal<Integer> USER_TYPE = new ThreadLocal<>();

    private AsyncUser() {
    }

    public static void set(Long userId, Integer userType) {
        USER_ID.set(userId);
        USER_TYPE.set(userType);
    }

    public static Long getUserId() {
        return USER_ID.get();
    }

    public static Integer getUserType() {
        return USER_TYPE.get();
    }

    public static void clear() {
        USER_ID.remove();
        USER_TYPE.remove();
    }
}
