package com.lms.kb.rag;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lms.kb.config.AiProperties;
import com.lms.kb.knowledge.constants.KbErrorInfo;
import com.lms.common.exceptions.CommonException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * DashScope 原生 API 客户端（对应 Python 版 llm.py 的 _DashScopeEmbeddings 与 rerank()）
 *
 * embedding / rerank 不走 OpenAI 兼容端点（兼容端点对 text-embedding-v3 报 400），
 * 直接调 DashScope 原生服务端点。OpenAI 兼容端点只给 AgentScope 的 qwen-vl 用。
 */
@Slf4j
@Component
public class DashScopeClient {

    private final AiProperties props;
    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    public DashScopeClient(AiProperties props, ObjectMapper objectMapper) {
        this.props = props;
        this.objectMapper = objectMapper;
        this.webClient = WebClient.builder()
                .baseUrl(props.getDashscopeNativeUrl())
                .defaultHeader("Authorization", "Bearer " + props.getDashscopeApiKey())
                .defaultHeader("Content-Type", "application/json")
                .build();
    }

    /** 文本向量化（单条），对应 embed_query */
    public double[] embed(String text) {
        return embed(List.of(text)).get(0);
    }

    /**
     * 文本批量向量化（单次请求上限 10 条），对应 embed_documents
     */
    public List<double[]> embed(List<String> texts) {
        if (texts.isEmpty()) {
            return List.of();
        }
        if (props.getDashscopeApiKey() == null || props.getDashscopeApiKey().isBlank()) {
            throw new CommonException(KbErrorInfo.AI_KEY_MISSING);
        }
        List<double[]> out = new ArrayList<>();
        for (int i = 0; i < texts.size(); i += 10) {
            List<String> batch = texts.subList(i, Math.min(i + 10, texts.size()));
            out.addAll(embedBatch(batch));
        }
        return out;
    }

    private List<double[]> embedBatch(List<String> batch) {
        JsonNode resp = post("/services/embeddings/text-embedding/text-embedding", Map.of(
                "model", props.getEmbeddingModel(),
                "input", Map.of("texts", batch)
        ));
        JsonNode embeddings = resp.path("output").path("embeddings");
        // 按 text_index 排序保证与入参顺序一致
        double[][] byIndex = new double[batch.size()][];
        for (JsonNode node : embeddings) {
            int idx = node.path("text_index").asInt();
            JsonNode vec = node.path("embedding");
            double[] arr = new double[vec.size()];
            for (int i = 0; i < vec.size(); i++) {
                arr[i] = vec.get(i).asDouble();
            }
            byIndex[idx] = arr;
        }
        List<double[]> out = new ArrayList<>();
        for (double[] arr : byIndex) {
            out.add(arr);
        }
        return out;
    }

    /**
     * 精排：对 documents 按与 query 的相关度降序，返回「原下标」列表，对应 rerank()
     */
    public List<Integer> rerank(String query, List<String> documents, int topN) {
        if (documents.isEmpty()) {
            return List.of();
        }
        if (props.getDashscopeApiKey() == null || props.getDashscopeApiKey().isBlank()) {
            throw new CommonException(KbErrorInfo.AI_KEY_MISSING);
        }
        JsonNode resp = post("/services/rerank/text-rerank/text-rerank", Map.of(
                "model", props.getRerankModel(),
                "input", Map.of("query", query, "documents", documents),
                "parameters", Map.of("top_n", Math.min(topN, documents.size()))
        ));
        JsonNode results = resp.path("output").path("results");
        List<int[]> indexed = new ArrayList<>();
        for (JsonNode node : results) {
            indexed.add(new int[]{node.path("index").asInt(),
                    (int) Math.round(node.path("relevance_score").asDouble() * 10000)});
        }
        indexed.sort((a, b) -> Integer.compare(b[1], a[1]));
        return indexed.stream().map(x -> x[0]).toList();
    }

    private JsonNode post(String path, Object body) {
        try {
            String raw = webClient.post().uri(path).bodyValue(body)
                    .retrieve().bodyToMono(String.class).block();
            JsonNode node = objectMapper.readTree(raw);
            if (!node.has("output")) {
                throw new CommonException(KbErrorInfo.DOC_PROCESS_FAILED.getCode(),
                        "DashScope API 响应异常: " + (node.path("message").asText("unknown")) + " | " + raw);
            }
            return node;
        } catch (CommonException e) {
            throw e;
        } catch (Exception e) {
            log.error("DashScope API 调用失败: {}", e.getMessage());
            throw new CommonException(KbErrorInfo.DOC_PROCESS_FAILED.getCode(),
                    "DashScope API 调用失败: " + e.getMessage());
        }
    }
}
