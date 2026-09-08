package com.lms.ai.harness;

/**
 * PolicyEngine 统一策略决策（spec HEAVY_HARNESS_SPEC §8.1）
 *
 * @param verdict       ALLOW / DENY / ASK（HITL 人工确认）
 * @param code          结构化拒绝原因码：WHITELIST(幻觉/越权) DEPTH(深度) ROLE(角色矩阵)
 *                      HIGH_RISK(高危直接拒) HITL_UNAVAILABLE(无人工通道) UNKNOWN_TOOL(未注册工具) —— Trace 用
 * @param reason        人类可读原因（回喂 LLM 让其重规划/转述用户）
 * @param hitlRequestId ASK 时非空（人工确认请求 id）
 */
public record PolicyDecision(
        Verdict verdict,
        String code,
        String reason,
        String hitlRequestId) {

    public enum Verdict {
        ALLOW,
        DENY,
        ASK
    }

    public boolean isAllowed() {
        return verdict == Verdict.ALLOW;
    }

    public boolean isAsk() {
        return verdict == Verdict.ASK;
    }

    public static PolicyDecision allow() {
        return new PolicyDecision(Verdict.ALLOW, "ALLOW", "放行", null);
    }

    public static PolicyDecision allow(String reason) {
        return new PolicyDecision(Verdict.ALLOW, "ALLOW", reason, null);
    }

    public static PolicyDecision deny(String code, String reason) {
        return new PolicyDecision(Verdict.DENY, code, reason, null);
    }

    public static PolicyDecision ask(String code, String requestId, String reason) {
        return new PolicyDecision(Verdict.ASK, code, reason, requestId);
    }
}
