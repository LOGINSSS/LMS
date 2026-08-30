package com.lms.kb.rag;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lms.common.exceptions.CommonException;
import com.lms.kb.config.AiProperties;
import com.lms.kb.config.KbProperties;
import com.lms.kb.eval.EvalSampleCollector;
import com.lms.kb.knowledge.constants.KbErrorInfo;
import com.lms.kb.knowledge.domain.po.KnowledgeBase;
import com.lms.kb.knowledge.mapper.KnowledgeBaseMapper;
import com.lms.kb.rag.domain.RagChunk;
import com.lms.kb.rag.domain.RagRequest;
import com.lms.kb.rag.domain.RagResponse;
import com.lms.kb.rag.domain.RagSource;
import com.lms.kb.rag.store.EsKnowledge;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.message.UserMessage;
import io.agentscope.core.model.ChatResponse;
import io.agentscope.core.model.GenerateOptions;
import io.agentscope.core.model.Model;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * RAG 五步流水线（对应 Python 版 graph.py 的 LangGraph：
 * START -> rewrite -> hyde -> retrieve -> rerank -> generate -> END）
 *
 * AgentScope Java 没有 LangGraph 式的图编排 API，这里用 Service 方法链等价实现
 * 每个图节点，且每步产物都保留供 RAGAS 评估采集：
 * 1. rewrite ：DeepSeek-R1 把口语化问题改写成利于检索的查询
 * 2. hyde    ：chat 模型生成假设性回答，作为一路检索 query
 * 3. retrieve：对 [改写, hyde, 原始] 各做一次混合检索（ES BM25+dense RRF），合并去重
 * 4. rerank  ：DashScope gte-rerank 对候选精排，取 top-K
 * 5. generate：Qwen-VL 依据 top-K 片段生成答案（附来源）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RagService {

    // ---------- 提示词（与 Python 版一致） ----------

    private static final String REWRITE_PROMPT = """
            你是一个检索查询改写器。请把用户的问题改写成 1 个更适合向量检索和全文检索的查询。
            要求：保留原意、补全隐含的上下文、去掉口语填充词，只输出改写后的查询，不要解释。

            用户问题：%s

            改写后的查询：""";

    private static final String HYDE_PROMPT = """
            你是一个知识助手。请根据你的知识，写一段能回答下面问题的文字（200 字以内）。
            这段文字会被用来做检索，所以请写得更接近「知识库中可能存在的原文表达」。

            问题：%s

            假设性回答：""";

    private static final String GENERATE_PROMPT = """
            根据下面给出的参考资料回答用户问题。只依据参考资料作答，不要编造。
            如果资料不足以回答，请直接说明「现有资料不足以回答」。

            参考资料：
            %s

            用户问题：%s

            回答：""";

    /**
     * 全部 AgentScope 模型实例（bean 名：rewriteModel/hydeModel/generatorModel）。
     * 用 Map 注入避免 @Qualifier 在 Lombok 构造器上的复制问题。
     */
    private final Map<String, Model> models;

    private final EsKnowledge esKnowledge;
    private final DashScopeClient dashScopeClient;
    private final KbProperties kbProperties;
    private final AiProperties aiProperties;
    private final KnowledgeBaseMapper kbMapper;
    private final EvalSampleCollector evalSampleCollector;

    /** 执行一次完整 RAG（对应 run_rag） */
    public RagResponse rag(RagRequest request) {
        String question = request.question();

        // 0. 解析归属：个人知识库 ownerType=2 + ownerId；课程库 ownerType=1 + courseId（兼容旧调用）
        int ownerType = request.ownerType() == null ? 1 : request.ownerType();
        Long ownerId;
        if (ownerType == 2) {
            ownerId = request.ownerId();
            if (ownerId == null) {
                throw new CommonException(KbErrorInfo.KB_NOT_FOUND.getCode(), "个人知识库检索需要 ownerId(userId)");
            }
        } else {
            ownerId = request.courseId();
            if (ownerId == null) {
                throw new CommonException(KbErrorInfo.KB_NOT_FOUND.getCode(), "课程知识库检索需要 courseId");
            }
        }
        Long courseId = ownerType == 2 ? null : ownerId;

        // 0. 校验知识库存在且可用（按归属查询，spec §4.4）
        KnowledgeBase kb = findKb(ownerType, ownerId);
        if (kb.getStatus() == null || kb.getStatus() != 1) {
            throw new CommonException(KbErrorInfo.KB_DISABLED);
        }
        checkKeys();

        // 1. rewrite（R1）
        String rewritten = rewriteNode(question);
        // 2. hyde
        String hyde = hydeNode(question);
        // 3. retrieve（三路混合检索合并去重，按归属隔离）
        List<RagChunk> retrieved = retrieveNode(List.of(question, rewritten, hyde), ownerType, ownerId);
        // 4. rerank
        List<RagChunk> reranked = rerankNode(question, retrieved);
        // 5. generate
        RagResponse response = generateNode(question, reranked);

        // 6. RAGAS 评估采集（中间产物 + 结果快照）
        evalSampleCollector.collect(courseId, question, rewritten, hyde, reranked, response);

        log.debug("RAG 完成 ownerType={} ownerId={} rewritten={} hydeLen={} retrieved={} reranked={}",
                ownerType, ownerId, rewritten, hyde.length(), retrieved.size(), reranked.size());
        return response;
    }

    /** 按归属查知识库：课程库按 course_id（兼容旧数据），个人库按 owner_type+owner_id */
    private KnowledgeBase findKb(int ownerType, Long ownerId) {
        LambdaQueryWrapper<KnowledgeBase> w = new LambdaQueryWrapper<>();
        if (ownerType == 2) {
            w.eq(KnowledgeBase::getOwnerType, 2).eq(KnowledgeBase::getOwnerId, ownerId);
        } else {
            w.eq(KnowledgeBase::getCourseId, ownerId);
        }
        KnowledgeBase kb = kbMapper.selectOne(w);
        if (kb == null) {
            throw new CommonException(KbErrorInfo.KB_NOT_FOUND);
        }
        return kb;
    }

    // ---------- 节点实现 ----------

    String rewriteNode(String question) {
        String prompt = REWRITE_PROMPT.formatted(question);
        return (callModel("rewriteModel", prompt) + "").strip();
    }

    String hydeNode(String question) {
        String prompt = HYDE_PROMPT.formatted(question);
        return (callModel("hydeModel", prompt) + "").strip();
    }

    List<RagChunk> retrieveNode(List<String> queries, Integer ownerType, Long ownerId) {
        List<RagChunk> merged = new ArrayList<>();
        for (String q : queries) {
            if (q == null || q.isBlank()) {
                continue;
            }
            try {
                merged.addAll(esKnowledge.hybridSearch(q, ownerType, ownerId, kbProperties.getFetchK()));
            } catch (Exception e) {
                log.warn("混合检索失败 query={}: {}", q, e.getMessage());
            }
        }
        return dedup(merged);
    }

    List<RagChunk> rerankNode(String question, List<RagChunk> docs) {
        if (docs.isEmpty()) {
            return List.of();
        }
        List<String> texts = docs.stream().map(RagChunk::text).toList();
        List<Integer> idxs = dashScopeClient.rerank(question, texts, kbProperties.getRerankTopK());
        return idxs.stream().map(docs::get).toList();
    }

    RagResponse generateNode(String question, List<RagChunk> reranked) {
        if (reranked.isEmpty()) {
            return new RagResponse("知识库为空或未检索到相关内容，请先入库文档。", List.of());
        }
        String context = IntStream.range(0, reranked.size())
                .mapToObj(i -> "[" + (i + 1) + "] " + reranked.get(i).text())
                .collect(Collectors.joining("\n\n"));
        String prompt = GENERATE_PROMPT.formatted(context, question);
        String answer = (callModel("generatorModel", prompt) + "").strip();
        List<RagSource> sources = reranked.stream()
                .map(r -> new RagSource(r.source(), r.docType(), r.text()))
                .toList();
        return new RagResponse(answer, sources);
    }

    // ---------- 工具 ----------

    /** AgentScope 模型调用：流式聚合为完整文本（与 lms-ai ChatService 相同模式） */
    private String callModel(String beanName, String prompt) {
        Model model = models.get(beanName);
        if (model == null) {
            throw new IllegalStateException("AgentScope 模型未配置: " + beanName);
        }
        List<Msg> msgs = List.of(new UserMessage(prompt));
        Flux<ChatResponse> flux = model.stream(msgs, List.of(), GenerateOptions.builder().build());
        return flux.flatMapIterable(ChatResponse::getContent)
                .filter(TextBlock.class::isInstance)
                .map(block -> ((TextBlock) block).getText())
                .collect(Collectors.joining())
                .block();
    }

    /** 按切片文本去重（对应 _dedup） */
    private List<RagChunk> dedup(List<RagChunk> docs) {
        Set<String> seen = new HashSet<>();
        List<RagChunk> out = new ArrayList<>();
        for (RagChunk d : docs) {
            if (seen.add(d.text())) {
                out.add(d);
            }
        }
        return out;
    }

    private void checkKeys() {
        if (aiProperties.getDeepseekApiKey() == null || aiProperties.getDeepseekApiKey().isBlank()
                || aiProperties.getDashscopeApiKey() == null || aiProperties.getDashscopeApiKey().isBlank()) {
            throw new CommonException(KbErrorInfo.AI_KEY_MISSING);
        }
    }
}
