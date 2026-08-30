package com.lms.kb.rag.domain;

/**
 * 答案来源（对应 Python 版 graph.py 的 sources：[{source, doc_type, text}]）
 */
public record RagSource(String source, String docType, String text) {
}
