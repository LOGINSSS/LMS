package com.lms.kb.ingest.domain;

import java.util.List;

/**
 * 文档解析结果（对应 Python 版 _extract_docx/_extract_pptx 的 (markdown正文, 额外chunks)）
 *
 * @param markdown 结构化 markdown 正文（走 split_text 分片）
 * @param chunks   原子 chunk（表格/图表/图片等，不参与字符兜底切分）
 */
public record ParseResult(String markdown, List<Chunk> chunks) {
}
