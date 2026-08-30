package com.lms.kb.ingest;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lms.kb.config.KbProperties;
import com.lms.kb.ingest.domain.Chunk;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Markdown 结构化分片（对应 Python 版 split_text：
 * MarkdownHeaderTextSplitter 按标题切 + 超长段 RecursiveCharacterTextSplitter 兜底）
 *
 * - 按 # ~ #### 标题层级归组，标题保留在切片内容里，层级写入 metadata（h1~h4）
 * - 无标题文本退化为递归字符切分（中文友好分隔符）
 */
@Component
@RequiredArgsConstructor
public class MarkdownSplitter {

    private static final Pattern HEADING = Pattern.compile("^(#{1,4})\\s+(.*)$");
    private static final List<String> SEPARATORS =
            List.of("\n\n", "\n", "。", "！", "？", "；", ".", "!", "?", ";", " ", "");

    private final KbProperties props;
    private final ObjectMapper objectMapper;

    /** 分片入口：返回 [{text, metadataJson}] */
    public List<Chunk> split(String markdown) {
        if (markdown == null || markdown.isBlank()) {
            return List.of();
        }
        List<Chunk> out = new ArrayList<>();
        Map<Integer, String> headers = new LinkedHashMap<>();
        StringBuilder buf = new StringBuilder();

        for (String line : markdown.split("\n", -1)) {
            Matcher m = HEADING.matcher(line);
            if (m.matches()) {
                // 新标题：flush 上一个 section
                if (!buf.isEmpty()) {
                    out.addAll(flush(buf.toString(), headers));
                    buf.setLength(0);
                }
                int level = m.group(1).length();
                // 清掉同级及更深的旧标题
                headers.keySet().removeIf(k -> k >= level);
                headers.put(level, m.group(2).trim());
                buf.append(line).append("\n");
            } else {
                buf.append(line).append("\n");
            }
        }
        if (!buf.isEmpty()) {
            out.addAll(flush(buf.toString(), headers));
        }
        return out;
    }

    /** 处理单个 section：短段直接成块，超长段（>1200 字符）走兜底切分 */
    private List<Chunk> flush(String text, Map<Integer, String> headers) {
        String trimmed = text.strip();
        if (trimmed.isEmpty()) {
            return List.of();
        }
        List<Chunk> out = new ArrayList<>();
        if (trimmed.length() > 1200) {
            for (String sub : recursiveSplit(trimmed, props.getChunkSize(), props.getChunkOverlap(), SEPARATORS)) {
                if (!sub.isBlank()) {
                    out.add(new Chunk(sub.strip(), meta(headers)));
                }
            }
        } else {
            out.add(new Chunk(trimmed, meta(headers)));
        }
        return out;
    }

    /** 递归字符切分：按分隔符列表找最后一个不超过 size 的切点（对应 RecursiveCharacterTextSplitter） */
    private List<String> recursiveSplit(String text, int size, int overlap, List<String> seps) {
        if (text.length() <= size) {
            return List.of(text);
        }
        if (seps.isEmpty()) {
            // 无分隔符可用：硬切
            List<String> out = new ArrayList<>();
            for (int i = 0; i < text.length(); i += size - overlap) {
                out.add(text.substring(i, Math.min(i + size, text.length())));
            }
            return out;
        }
        String sep = seps.get(0);
        int cut = -1;
        int from = Math.max(0, size - overlap);
        for (int i = from; i < text.length(); i++) {
            if (text.startsWith(sep, i)) {
                cut = i;
            }
        }
        if (cut < 0) {
            // 当前分隔符找不到合适切点 → 换下一级分隔符
            return recursiveSplit(text, size, overlap, seps.subList(1, seps.size()));
        }
        String head = text.substring(0, cut);
        String tail = text.substring(cut);
        List<String> out = new ArrayList<>();
        out.addAll(recursiveSplit(head, size, overlap, seps));
        out.addAll(recursiveSplit(tail, size, overlap, seps));
        return out;
    }

    private String meta(Map<Integer, String> headers) {
        try {
            Map<String, Object> map = new LinkedHashMap<>();
            headers.forEach((level, title) -> map.put("h" + level, title));
            return objectMapper.writeValueAsString(map);
        } catch (Exception e) {
            return "{}";
        }
    }
}
