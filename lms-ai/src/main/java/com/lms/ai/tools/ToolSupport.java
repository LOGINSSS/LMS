package com.lms.ai.tools;

import com.lms.ai.context.AgentContextHolder;
import com.lms.ai.context.AgentTaskContext;
import com.lms.common.domain.R;
import com.lms.common.exceptions.CommonException;
import com.lms.common.utils.JsonUtils;
import io.agentscope.core.agent.RuntimeContext;

/**
 * 工具基座（spec §5.3：任务上下文注入 / 用户身份桥接 / 结果序列化）
 */
public final class ToolSupport {

    /** RuntimeContext 键：用户 id（Long） */
    public static final String CTX_USER_ID = "lmsUserId";
    /** RuntimeContext 键：用户类型（Integer：1 学生 / 2 老师） */
    public static final String CTX_USER_TYPE = "lmsUserType";

    private ToolSupport() {
    }

    /**
     * 进入工具调用：从 RuntimeContext 取用户身份桥接到当前线程（Feign 拦截器据此补 user-info 头），
     * 返回用户 id（可能为 null —— 子 agent 经 SubAgentTool 调用时上下文由父级透传）。
     */
    public static Long enter(RuntimeContext ctx) {
        Long userId = ctx == null ? null : ctx.get(CTX_USER_ID, Long.class);
        Integer userType = ctx == null ? null : ctx.get(CTX_USER_TYPE, Integer.class);
        if (userId == null && ctx != null) {
            // 兜底：RuntimeContext 内建 userId（String）
            String uid = ctx.getUserId();
            if (uid != null && !uid.isBlank()) {
                try {
                    userId = Long.valueOf(uid);
                } catch (NumberFormatException ignored) {
                }
            }
        }
        AgentContextHolder.set(userId, userType);
        return userId;
    }

    /** 需要登录的工具：无用户上下文时抛出（业务模块也会拒绝，这里提前给出清晰错误） */
    public static Long requireUser(RuntimeContext ctx) {
        Long userId = enter(ctx);
        if (userId == null) {
            throw new CommonException("该操作需要登录用户上下文（请先登录）");
        }
        return userId;
    }

    /** 清理线程上下文（工具方法 finally 中调用） */
    public static void exit() {
        AgentContextHolder.clear();
    }

    /** 取任务上下文（可能为 null） */
    public static AgentTaskContext taskContext(RuntimeContext ctx) {
        return ctx == null ? null : ctx.get(AgentTaskContext.KEY, AgentTaskContext.class);
    }

    /** 校验 R 响应，成功返回 data，失败抛异常 */
    public static <T> T check(R<T> r) {
        if (r == null || !r.success()) {
            throw new CommonException(r == null ? "服务无响应" : r.getMsg());
        }
        return r.getData();
    }

    /** 结果序列化（给 LLM 阅读的 JSON 文本） */
    public static String json(Object data) {
        if (data == null) {
            return "null";
        }
        return JsonUtils.toJsonStr(data);
    }

    /** 失败文本（返回给 LLM，让其继续编排或说明原因） */
    public static String fail(Throwable e) {
        String msg = e.getMessage();
        return "调用失败: " + (msg == null ? e.getClass().getSimpleName() : msg);
    }
}
