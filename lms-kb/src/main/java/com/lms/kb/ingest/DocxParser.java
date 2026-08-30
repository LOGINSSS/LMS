package com.lms.kb.ingest;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lms.kb.ingest.domain.Chunk;
import com.lms.kb.ingest.domain.ParseResult;
import lombok.RequiredArgsConstructor;
import org.apache.poi.xwpf.usermodel.IBodyElement;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * docx 解析（对应 Python 版 _extract_docx）
 *
 * - 按文档顺序迭代段落与表格：标题样式（Heading 1/标题 1）转 markdown 标题走结构化分片
 * - 表格序列化为 markdown 表格作为原子 chunk（metadata 带当前标题层级上下文），不切碎
 */
@Component
@RequiredArgsConstructor
public class DocxParser {

    private static final Pattern HEADING_STYLE = Pattern.compile("^(?:heading|标题)\\s*(\\d+)$", Pattern.CASE_INSENSITIVE);

    private final ObjectMapper objectMapper;

    public ParseResult parse(Path file) throws IOException {
        try (XWPFDocument doc = new XWPFDocument(Files.newInputStream(file))) {
            List<String> lines = new ArrayList<>();
            List<Chunk> tableChunks = new ArrayList<>();
            Map<Integer, String> headers = new HashMap<>();

            for (IBodyElement el : doc.getBodyElements()) {
                if (el instanceof XWPFParagraph p) {
                    String text = p.getText() == null ? "" : p.getText().strip();
                    if (text.isEmpty()) {
                        continue;
                    }
                    int level = headingLevel(p.getStyle());
                    if (level > 0) {
                        headers.keySet().removeIf(k -> k >= level);
                        headers.put(level, text);
                        lines.add("#".repeat(level) + " " + text);
                    } else {
                        lines.add(text);
                    }
                } else if (el instanceof XWPFTable table) {
                    String md = tableToMarkdown(table);
                    if (!md.isEmpty()) {
                        Map<String, Object> meta = new LinkedHashMap<>();
                        meta.put("table", true);
                        headers.forEach((l, t) -> meta.put("h" + l, t));
                        tableChunks.add(new Chunk(md, json(meta)));
                    }
                }
            }
            return new ParseResult(String.join("\n", lines), tableChunks);
        }
    }

    /** 标题样式名 → 级别 1~6，非标题返回 0（对应 _heading_level） */
    static int headingLevel(String styleName) {
        if (styleName == null || styleName.isBlank()) {
            return 0;
        }
        String name = styleName.strip();
        Matcher m = HEADING_STYLE.matcher(name);
        if (m.matches()) {
            return Math.min(Integer.parseInt(m.group(1)), 6);
        }
        if ("title".equalsIgnoreCase(name) || "标题".equals(name)) {
            return 1;
        }
        return 0;
    }

    /** XWPFTable → markdown 表格字符串 */
    static String tableToMarkdown(XWPFTable table) {
        List<List<String>> rows = new ArrayList<>();
        int width = 0;
        for (var row : table.getRows()) {
            List<String> cells = new ArrayList<>();
            for (var cell : row.getTableCells()) {
                String text = cell.getText() == null ? "" : String.join(" ", cell.getText().split("\\s+"));
                cells.add(text);
            }
            width = Math.max(width, cells.size());
            rows.add(cells);
        }
        if (rows.isEmpty()) {
            return "";
        }
        List<List<String>> padded = new ArrayList<>();
        for (List<String> r : rows) {
            List<String> row = new ArrayList<>(r);
            while (row.size() < width) {
                row.add("");
            }
            padded.add(row);
        }
        StringBuilder sb = new StringBuilder();
        sb.append("| ").append(String.join(" | ", padded.get(0))).append(" |\n");
        sb.append("| ").append(String.join(" | ", java.util.Collections.nCopies(width, "---"))).append(" |");
        for (int i = 1; i < padded.size(); i++) {
            sb.append("\n| ").append(String.join(" | ", padded.get(i))).append(" |");
        }
        return sb.toString();
    }

    private String json(Map<String, Object> meta) {
        try {
            return objectMapper.writeValueAsString(meta);
        } catch (Exception e) {
            return "{}";
        }
    }
}
