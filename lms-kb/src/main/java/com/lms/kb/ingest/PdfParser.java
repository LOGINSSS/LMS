package com.lms.kb.ingest;

import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Path;

/**
 * PDF 文本提取（对应 Python 版 _pdf_to_markdown 的 PyMuPDF 兜底路径）
 *
 * 说明：Python 版首选 MinerU 做版面解析（OCR/公式/表格），Java 端没有等价库，
 * 这里用 PDFBox 纯文本提取；扫描版 PDF（无内嵌文字）返回空串由上层处理。
 * 后续需要版面级解析可对接自部署 MinerU HTTP 服务（见 README TODO）。
 */
@Slf4j
@Component
public class PdfParser {

    public String extractText(Path file) throws IOException {
        try (PDDocument doc = Loader.loadPDF(file.toFile())) {
            PDFTextStripper stripper = new PDFTextStripper();
            return stripper.getText(doc);
        }
    }
}
