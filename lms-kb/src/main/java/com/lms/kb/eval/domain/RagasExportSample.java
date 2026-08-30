package com.lms.kb.eval.domain;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * RAGAS 0.4.x 评估数据集导出样本
 *
 * 新版 ragas.Dataset 强制 4 个字段（旧版 question/contexts/answer/ground_truth 已废弃）：
 * - user_input         用户问题
 * - retrieved_contexts RAG 检索召回片段列表
 * - response           RAG 生成回答
 * - reference          标准答案（人工标注，Context Recall/Entities Recall/Noise Sensitivity 需要）
 */
public record RagasExportSample(
        @JsonProperty("user_input") String userInput,
        @JsonProperty("response") String response,
        @JsonProperty("retrieved_contexts") java.util.List<String> retrievedContexts,
        @JsonProperty("reference") String reference
) {
}
