package com.lms.kb.ingest;

import com.lms.kb.config.KbProperties;
import com.lms.kb.rag.DashScopeClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 向量化服务（对应 Python 版 _embed_and_insert 的 embedding 环节）
 *
 * 封装 DashScope text-embedding-v3 的批量调用（批上限见 KbProperties.embedBatchSize）。
 */
@Service
@RequiredArgsConstructor
public class EmbeddingService {

    private final DashScopeClient dashScopeClient;
    private final KbProperties kbProperties;

    /** 批量向量化，返回与入参顺序一致的向量列表 */
    public List<double[]> embedDocuments(List<String> texts) {
        return dashScopeClient.embed(texts);
    }

    /** 单条向量化（检索用） */
    public double[] embedQuery(String text) {
        return dashScopeClient.embed(text);
    }

    /** 批量向量化（按配置分批，进度感知） */
    public List<double[]> embedInBatches(List<String> texts) {
        List<double[]> out = new ArrayList<>();
        int batch = kbProperties.getEmbedBatchSize();
        for (int i = 0; i < texts.size(); i += batch) {
            out.addAll(dashScopeClient.embed(texts.subList(i, Math.min(i + batch, texts.size()))));
        }
        return out;
    }
}
