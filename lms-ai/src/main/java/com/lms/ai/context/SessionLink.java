package com.lms.ai.context;

import java.util.function.Supplier;

/**
 * 会话链接（P5：管道节点产物挂真实会话）
 *
 * 管道工具（LearningPipelineTools）从 RuntimeContext 取到数字会话号后，
 * 在调用管道服务的线程局部挂载（with），LearningPipelineService.persist 据此给
 * agent_task 行写 session_id/action_type=pipeline，实现任务树完整可见；
 * 同步调用链（评估 evaluate 内部串行节点）同线程自动透传。
 */
public final class SessionLink {

    private static final ThreadLocal<Long> CURRENT = new ThreadLocal<>();

    private SessionLink() {
    }

    /** 在当前线程挂会话号并执行 */
    public static <T> T with(Long sessionId, Supplier<T> supplier) {
        Long prev = CURRENT.get();
        CURRENT.set(sessionId);
        try {
            return supplier.get();
        } finally {
            if (prev == null) {
                CURRENT.remove();
            } else {
                CURRENT.set(prev);
            }
        }
    }

    /** 当前线程会话号（可能 null=非会话上下文） */
    public static Long current() {
        return CURRENT.get();
    }
}
