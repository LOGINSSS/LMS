package com.lms.kb.rag.domain;

/**
 * 检索结果切片（对应 Python 版 hybrid_search 返回的 dict：text/source/doc_type/metadata/score）
 *
 * @param text     切片文本
 * @param source   来源文件名
 * @param docType  文档类型
 * @param metadata 元数据 JSON（标题层级/表格标记等）
 * @param score    相关度分数（RRF 融合后）
 */
public record RagChunk(String text, String source, String docType, String metadata, double score) {
}
