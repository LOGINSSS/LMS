package com.lms.ai.harness;

/**
 * 被管控动作类型（spec HEAVY_HARNESS_SPEC §8.1：PolicyEngine 统一判定入口）
 *
 * - INVITE_AGENT：邀请专家（v1 GuardedAgentTool 的拦截面）
 * - CALL_TOOL：调用写/读工具（P1 ToolGateway 接入后生效）
 * - SCHEDULE_TASK：发布定时/管道任务（P5 任务板接入后生效）
 *
 * HITL 批复缓存按 (sessionId, actionType, actionId) 收敛：同会话同动作窗口内免重复确认。
 */
public enum ActionType {
    INVITE_AGENT,
    CALL_TOOL,
    SCHEDULE_TASK;

    /** Redis/Trace 中使用的中缀 */
    public String suffix() {
        return name();
    }
}
