package com.lms.kb.rag.domain;

import java.util.List;

/**
 * RAG 问答响应
 *
 * @param answer  生成答案
 * @param sources 答案来源（供溯源与 RAGAS 评估）
 */
public record RagResponse(String answer, List<RagSource> sources) {
}
