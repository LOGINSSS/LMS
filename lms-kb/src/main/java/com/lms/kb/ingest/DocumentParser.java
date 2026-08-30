package com.lms.kb.ingest;

import com.lms.common.exceptions.CommonException;
import com.lms.kb.ingest.domain.Chunk;
import com.lms.kb.ingest.domain.ParseResult;
import com.lms.kb.knowledge.constants.KbErrorInfo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * 文档解析入口（对应 Python 版 ingest.py 的 ingest_file 分派逻辑）
 *
 * 按扩展名分派：md/txt 直读 → markdown 分片；docx/pptx 结构化解析（表格/图片原子 chunk）；
 * pdf PDFBox 文本 → markdown 分片；图片 Qwen-VL 描述 + OCR。
 */
@Component
@RequiredArgsConstructor
public class DocumentParser {

    private final MarkdownSplitter splitter;
    private final ImageProcessor imageProcessor;
    private final DocxParser docxParser;
    private final PptxParser pptxParser;
    private final PdfParser pdfParser;

    public List<Chunk> parse(Path file, String ext, String source) throws IOException {
        return switch (ext) {
            case "md", "markdown", "txt" -> splitter.split(Files.readString(file));
            case "docx" -> docx(file);
            case "pptx" -> pptx(file);
            case "pdf" -> splitter.split(pdfParser.extractText(file));
            case "png", "jpg", "jpeg", "bmp", "webp" -> imageProcessor.toChunks(file);
            default -> throw new CommonException(KbErrorInfo.DOC_TYPE_UNSUPPORTED);
        };
    }

    private List<Chunk> docx(Path file) throws IOException {
        ParseResult r = docxParser.parse(file);
        List<Chunk> out = new ArrayList<>(splitter.split(r.markdown()));
        out.addAll(r.chunks());
        return out;
    }

    private List<Chunk> pptx(Path file) throws IOException {
        ParseResult r = pptxParser.parse(file);
        List<Chunk> out = new ArrayList<>(splitter.split(r.markdown()));
        out.addAll(r.chunks());
        return out;
    }
}
