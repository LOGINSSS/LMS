package com.lms.kb.knowledge.domain.es;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * ES 切片文档（索引 lms_kb_chunk）
 *
 * 对应 Python 版 Milvus 集合：text/dense/source/doc_type/metadata + 归属隔离字段。
 * embedding 为 dense_vector(COSINE)，text 走 BM25（analyzer 可配 ik_smart）。
 * 归属字段（spec §4.4）：课程知识库写 owner_type=1 + owner_id=courseId（course_id 冗余）；
 * 个人知识库写 owner_type=2 + owner_id=userId，检索按 owner 强过滤实现隔离。
 */
public record KbChunkDoc(
        @JsonProperty("text") String text,
        @JsonProperty("embedding") double[] embedding,
        @JsonProperty("source") String source,
        @JsonProperty("doc_type") String docType,
        @JsonProperty("metadata") String metadata,
        @JsonProperty("kb_id") Long kbId,
        @JsonProperty("owner_type") Integer ownerType,
        @JsonProperty("owner_id") Long ownerId,
        @JsonProperty("course_id") Long courseId,
        @JsonProperty("doc_id") String docId,
        @JsonProperty("chunk_seq") int chunkSeq
) {
}
