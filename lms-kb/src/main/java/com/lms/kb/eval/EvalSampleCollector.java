package com.lms.kb.eval;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lms.kb.knowledge.domain.po.RagEvalSample;
import com.lms.kb.knowledge.mapper.RagEvalSampleMapper;
import com.lms.kb.rag.domain.RagChunk;
import com.lms.kb.rag.domain.RagResponse;
import com.lms.kb.rag.domain.RagSource;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * RAGAS 评估样本采集器
 *
 * 每次 RAG 问答把链路中间产物（question/rewritten/hyde/contexts/answer/sources）
 * 落库为 rag_eval_sample，供导出为 ragas 评估数据集离线量化
 * （faithfulness / answer_relevancy / context_precision / context_recall 等）。
 */
@Component
@RequiredArgsConstructor
public class EvalSampleCollector {

    private final RagEvalSampleMapper sampleMapper;
    private final ObjectMapper objectMapper;

    public void collect(Long courseId, String question, String rewritten, String hyde,
                        List<RagChunk> contexts, RagResponse response) {
        try {
            RagEvalSample sample = new RagEvalSample();
            sample.setCourseId(courseId);
            sample.setQuestion(question);
            sample.setRewritten(rewritten);
            sample.setHyde(hyde);
            sample.setContexts(toJson(contexts));
            sample.setAnswer(response.answer());
            sample.setSources(toJson(response.sources()));
            sampleMapper.insert(sample);
        } catch (Exception e) {
            // 评估采集失败不影响问答主流程
            org.slf4j.LoggerFactory.getLogger(getClass()).warn("评估样本采集失败: {}", e.getMessage());
        }
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            return "[]";
        }
    }
}
