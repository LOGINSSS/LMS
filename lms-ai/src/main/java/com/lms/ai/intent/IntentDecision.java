package com.lms.ai.intent;

/**
 * 意图决策（L0 输出，spec HEAVY_HARNESS_SPEC §4.1/§4.4）
 *
 * @param intent     识别意图（UNKNOWN = 兜底自由 ReAct）
 * @param confidence 置信度（确定性规则=1.0，LLM 分类层=模型输出）
 * @param source     识别来源：rule（确定性快捷入口）/ llm（分类层）/ none（兜底）
 * @param hint       给 system prompt 的提示语（可选，降低 LLM 误路由概率，非安全屏障）
 */
public record IntentDecision(
        Intent intent,
        double confidence,
        String source,
        String hint) {

    public static IntentDecision unknown() {
        return new IntentDecision(Intent.UNKNOWN, 0, "none", null);
    }

    public static IntentDecision rule(Intent intent, String hint) {
        return new IntentDecision(intent, 1.0, "rule", hint);
    }

    public boolean isKnown() {
        return intent != null && intent.isKnown();
    }
}
