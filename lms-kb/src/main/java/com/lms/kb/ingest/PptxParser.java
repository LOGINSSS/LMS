package com.lms.kb.ingest;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lms.kb.ingest.domain.Chunk;
import com.lms.kb.ingest.domain.ParseResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xslf.usermodel.XSLFPictureData;
import org.apache.poi.xslf.usermodel.XSLFPictureShape;
import org.apache.poi.xslf.usermodel.XSLFShape;
import org.apache.poi.xslf.usermodel.XSLFSlide;
import org.apache.poi.xslf.usermodel.XSLFTable;
import org.apache.poi.xslf.usermodel.XSLFTableCell;
import org.apache.poi.xslf.usermodel.XSLFTableRow;
import org.apache.poi.xslf.usermodel.XSLFTextShape;
import org.springframework.stereotype.Component;

import java.awt.geom.Rectangle2D;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * pptx 解析（对应 Python 版 _extract_pptx）
 *
 * - 文本按页组织（## 第 N 页）；表格转 markdown 表格原子 chunk
 * - 有意义的图片（尺寸 ≥ 80px）走 Qwen-VL 描述 + OCR
 * - 图表（XSLFChart）POI 支持有限，跳过（与 Python 版 chart 转 markdown 的能力差异，
 *   有需要可后续用图表数据接口补齐）
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PptxParser {

    private static final double EMU_PER_PX = 914400.0 / 96.0;
    private static final double IMAGE_MIN_PX = 80;

    private final ImageProcessor imageProcessor;
    private final ObjectMapper objectMapper;

    public ParseResult parse(Path file) throws IOException {
        try (XMLSlideShow ppt = new XMLSlideShow(Files.newInputStream(file))) {
            List<String> lines = new ArrayList<>();
            List<Chunk> chunks = new ArrayList<>();
            List<Path> tmpImages = new ArrayList<>();
            int idx = 1;

            for (XSLFSlide slide : ppt.getSlides()) {
                List<String> pageLines = new ArrayList<>();
                pageLines.add("## 第 " + idx + " 页");
                boolean hasText = false;

                for (XSLFShape shape : slide.getShapes()) {
                    if (shape instanceof XSLFPictureShape pic) {
                        Rectangle2D anchor = pic.getAnchor();
                        if (anchor.getWidth() / EMU_PER_PX < IMAGE_MIN_PX
                                || anchor.getHeight() / EMU_PER_PX < IMAGE_MIN_PX) {
                            continue; // 装饰小图丢弃
                        }
                        try {
                            XSLFPictureData data = pic.getPictureData();
                            String ext = data.getContentType().contains("png") ? "png" : "jpg";
                            Path tmp = Files.createTempFile("pptx_pic_", "." + ext);
                            Files.write(tmp, data.getData());
                            tmpImages.add(tmp);
                            chunks.addAll(imageProcessor.toChunks(tmp));
                            hasText = true;
                        } catch (Exception e) {
                            log.warn("pptx 图片处理失败: {}", e.getMessage());
                        }
                        continue;
                    }
                    if (shape instanceof XSLFTable table) {
                        String md = tableToMarkdown(table);
                        if (!md.isEmpty()) {
                            Map<String, Object> meta = new LinkedHashMap<>();
                            meta.put("table", true);
                            meta.put("page", idx);
                            chunks.add(new Chunk(md, json(meta)));
                        }
                        hasText = true;
                        continue;
                    }
                    if (shape instanceof XSLFTextShape ts) {
                        String text = ts.getText() == null ? "" : ts.getText().strip();
                        if (!text.isEmpty()) {
                            pageLines.add(text);
                            hasText = true;
                        }
                    }
                }
                if (hasText) {
                    lines.add(String.join("\n", pageLines));
                }
                idx++;
            }

            for (Path tmp : tmpImages) {
                try {
                    Files.deleteIfExists(tmp);
                } catch (Exception ignored) {
                }
            }
            return new ParseResult(String.join("\n", lines), chunks);
        }
    }

    /** XSLFTable → markdown 表格 */
    static String tableToMarkdown(XSLFTable table) {
        List<List<String>> rows = new ArrayList<>();
        int width = 0;
        for (XSLFTableRow row : table.getRows()) {
            List<String> cells = new ArrayList<>();
            for (XSLFTableCell cell : row.getCells()) {
                cells.add(cell.getText() == null ? "" : cell.getText().replaceAll("\\s+", " ").strip());
            }
            width = Math.max(width, cells.size());
            rows.add(cells);
        }
        if (rows.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("| ").append(String.join(" | ", pad(rows.get(0), width))).append(" |\n");
        sb.append("| ").append(String.join(" | ", java.util.Collections.nCopies(width, "---"))).append(" |");
        for (int i = 1; i < rows.size(); i++) {
            sb.append("\n| ").append(String.join(" | ", pad(rows.get(i), width))).append(" |");
        }
        return sb.toString();
    }

    private static List<String> pad(List<String> row, int width) {
        List<String> out = new ArrayList<>(row);
        while (out.size() < width) {
            out.add("");
        }
        return out;
    }

    private String json(Map<String, Object> meta) {
        try {
            return objectMapper.writeValueAsString(meta);
        } catch (Exception e) {
            return "{}";
        }
    }
}
