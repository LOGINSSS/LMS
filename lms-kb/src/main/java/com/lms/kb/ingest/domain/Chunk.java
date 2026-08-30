package com.lms.kb.ingest.domain;

/**
 * 解析/分片产物（对应 Python 版 ingest.py 的 chunk：{text, metadata}）
 *
 * @param text         切片文本
 * @param metadataJson 元数据 JSON 字符串（标题层级 h1~h4、table/kind/page 等）
 */
public record Chunk(String text, String metadataJson) {
}
