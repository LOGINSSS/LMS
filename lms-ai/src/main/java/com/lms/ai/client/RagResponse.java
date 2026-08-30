package com.lms.ai.client;

import java.util.List;

/**
 * 课程知识库 RAG 问答响应（与 lms-kb RagResponse 契约一致）
 *
 * @param answer  生成答案
 * @param sources 答案来源（供溯源与 RAGAS 评估）
 */
public record RagResponse(String answer, List<RagSource> sources) {

    /** 答案来源 */
    public record RagSource(String source, String docType, String text) {
    }
}
