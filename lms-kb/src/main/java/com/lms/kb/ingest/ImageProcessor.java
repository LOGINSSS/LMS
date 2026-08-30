package com.lms.kb.ingest;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lms.kb.config.AiProperties;
import com.lms.kb.ingest.domain.Chunk;
import com.lms.kb.knowledge.constants.KbErrorInfo;
import com.lms.common.exceptions.CommonException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;

/**
 * 图片处理（对应 Python 版 ingest.py 的 _describe_image / _ocr_image）
 *
 * 用 Qwen-VL（DashScope OpenAI 兼容端点，多模态）做两件事：
 * - describe：语义描述（图表数据、关键内容）
 * - ocr：图片文字提取
 * Java 端没有 RapidOCR 的等价物，统一交给 Qwen-VL，效果一致且零本地依赖。
 */
@Slf4j
@Component
public class ImageProcessor {

    private static final Map<String, String> MIME = Map.of(
            "png", "image/png", "jpg", "image/jpeg", "jpeg", "image/jpeg",
            "bmp", "image/bmp", "webp", "image/webp");

    private final AiProperties props;
    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    public ImageProcessor(AiProperties props, ObjectMapper objectMapper) {
        this.props = props;
        this.objectMapper = objectMapper;
        this.webClient = WebClient.builder()
                .baseUrl(props.getDashscopeBaseUrl())
                .defaultHeader("Authorization", "Bearer " + props.getDashscopeApiKey())
                .defaultHeader("Content-Type", "application/json")
                .build();
    }

    /** 图片 → 两个 chunk：[图片描述] + [图片OCR文字]（对应 _picture_chunks） */
    public List<Chunk> toChunks(Path image) {
        List<Chunk> out = new ArrayList<>();
        String description = describe(image);
        if (description != null && !description.isBlank()) {
            out.add(new Chunk("[图片描述] " + description, "{\"kind\":\"image_description\"}"));
        }
        String ocr = ocr(image);
        if (ocr != null && !ocr.isBlank()) {
            out.add(new Chunk("[图片OCR文字] " + ocr, "{\"kind\":\"image_ocr\"}"));
        }
        return out;
    }

    /** Qwen-VL 语义描述（对应 _describe_image） */
    public String describe(Path image) {
        return callVlm(image, "详细描述这张图片的内容、关键文字和图表数据。");
    }

    /** Qwen-VL OCR（对应 RapidOCR） */
    public String ocr(Path image) {
        return callVlm(image, "请提取这张图片中的所有文字，按阅读顺序输出，不要额外解释。");
    }

    private String callVlm(Path image, String prompt) {
        try {
            if (props.getDashscopeApiKey() == null || props.getDashscopeApiKey().isBlank()) {
                throw new CommonException(KbErrorInfo.AI_KEY_MISSING);
            }
            String ext = image.getFileName().toString().contains(".")
                    ? image.getFileName().toString().substring(image.getFileName().toString().lastIndexOf('.') + 1).toLowerCase()
                    : "png";
            String mime = MIME.getOrDefault(ext, "image/png");
            String b64 = Base64.getEncoder().encodeToString(Files.readAllBytes(image));

            Map<String, Object> body = Map.of(
                    "model", props.getQwenVlModel(),
                    "messages", List.of(Map.of(
                            "role", "user",
                            "content", List.of(
                                    Map.of("type", "text", "text", prompt),
                                    Map.of("type", "image_url",
                                            "image_url", Map.of("url", "data:" + mime + ";base64," + b64))))),
                    "temperature", 0);

            String raw = webClient.post().uri("/chat/completions").bodyValue(body)
                    .retrieve().bodyToMono(String.class).block();
            JsonNode node = objectMapper.readTree(raw);
            JsonNode content = node.path("choices").path(0).path("message").path("content");
            return content.isTextual() ? content.asText() : content.toString();
        } catch (CommonException e) {
            throw e;
        } catch (Exception e) {
            log.warn("图片 VLM 调用失败 {}: {}", image.getFileName(), e.getMessage());
            return "";
        }
    }
}
