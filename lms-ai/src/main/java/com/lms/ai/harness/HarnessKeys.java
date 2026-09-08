package com.lms.ai.harness;

/**
 * Harness 全局常量（Redis 键前缀 / RuntimeContext 键名，spec GLOBAL_HARNESS_SPEC §4）
 *
 * 所有状态（HITL 请求/批复、Trace 环形缓冲、会话用量）收敛在 Redis，
 * 按 sessionId 隔离，TTL 内有效，不引入专家 Agent 侧任何状态。
 */
public final class HarnessKeys {

    private HarnessKeys() {
    }

    // ---------- RuntimeContext 键 ----------

    /** 邀请链当前深度（Integer；顶层调用缺省 0，由 GuardedAgentTool 沿链维护并恢复） */
    public static final String CTX_INVITE_DEPTH = "lmsHarnessInviteDepth";

    /** 管道令牌（String；编排层签发，经 ctx 透传给管道内节点工具，PolicyEngine 校验豁免） */
    public static final String CTX_PIPELINE_TOKEN = "lmsPipelineToken";

    /** 当前轮意图（String：Intent 枚举名；L0 IntentRouter 写入，供策略/Flow 判定与 Trace 消费） */
    public static final String CTX_INTENT = "lmsIntent";

    /** 动态工具开关键（value off/on）：tool:switch:{toolId} */
    public static final String TOOL_SWITCH_PREFIX = "tool:switch:";

    /** 管道令牌（一次性）：agent:pipeline:tok:{token} */
    public static final String PIPELINE_TOKEN_PREFIX = "agent:pipeline:tok:";

    // ---------- Redis 键前缀（v1，GLOBAL_HARNESS_SPEC） ----------

    /** HITL 确认请求（hash）：agent:hitl:req:{requestId}（v1 遗留，TTL 自然过期） */
    public static final String HITL_REQ_PREFIX = "agent:hitl:req:";

    /** HITL 批复缓存（value=1）：agent:hitl:ok:{sessionId}:{target}（v1 遗留） */
    public static final String HITL_OK_PREFIX = "agent:hitl:ok:";

    /** HITL 拒绝缓存（value=1）：agent:hitl:no:{sessionId}:{target}（v1 遗留） */
    public static final String HITL_NO_PREFIX = "agent:hitl:no:";

    // ---------- Redis 键前缀（v2 动作级，HEAVY_HARNESS_SPEC §8.5） ----------

    /** 动作级确认请求（hash）：agent:hitl2:req:{requestId}（含 actionType/actionId） */
    public static final String HITL2_REQ_PREFIX = "agent:hitl2:req:";

    /** 动作级批复缓存（value=1）：agent:hitl2:ok:{sessionId}:{actionType}:{actionId} */
    public static final String HITL2_OK_PREFIX = "agent:hitl2:ok:";

    /** 动作级拒绝缓存（value=1）：agent:hitl2:no:{sessionId}:{actionType}:{actionId} */
    public static final String HITL2_NO_PREFIX = "agent:hitl2:no:";

    /** Trace 环形缓冲（list）：agent:trace:{sessionId} */
    public static final String TRACE_PREFIX = "agent:trace:";

    /** 会话用量（hash：turns/tokens）：agent:usage:{sessionId} */
    public static final String USAGE_PREFIX = "agent:usage:";

    /** HITL 请求状态 */
    public static final int HITL_PENDING = 0;
    public static final int HITL_APPROVED = 1;
    public static final int HITL_REJECTED = 2;
}
