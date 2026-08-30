package com.lms.kb.ingest;

import com.lms.kb.config.KbProperties;
import com.lms.kb.ingest.domain.Chunk;
import com.lms.kb.knowledge.domain.es.KbChunkDoc;
import com.lms.kb.knowledge.domain.po.KnowledgeBase;
import com.lms.kb.knowledge.domain.po.KnowledgeDoc;
import com.lms.kb.knowledge.mapper.KnowledgeBaseMapper;
import com.lms.kb.knowledge.mapper.KnowledgeDocMapper;
import com.lms.kb.rag.store.EsKnowledge;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * 文档入库管道（对应 Python 版 ingest.py 的解析 → 分片 → embedding → 入库流程）
 *
 * 状态机：0待解析 → 1解析中 → 2向量化中 → 3完成；任一步失败置 4 并记录原因。
 * 文档上传后由 KbServiceImpl 异步触发，不阻塞上传接口。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DocPipelineService {

    private static final int STATUS_PENDING = 0;
    private static final int STATUS_PARSING = 1;
    private static final int STATUS_EMBEDDING = 2;
    private static final int STATUS_DONE = 3;
    private static final int STATUS_FAILED = 4;

    private final KnowledgeDocMapper docMapper;
    private final KnowledgeBaseMapper kbMapper;
    private final KbProperties kbProperties;
    private final DocumentParser documentParser;
    private final EmbeddingService embeddingService;
    private final EsKnowledge esKnowledge;

    /**
     * 处理单个文档：解析 → 分片 → 向量化 → 写 ES → 更新状态与计数
     */
    @Async("kbPipelineExecutor")
    public void processDoc(Long docId) {
        KnowledgeDoc doc = docMapper.selectById(docId);
        if (doc == null) {
            return;
        }
        try {
            updateStatus(doc, STATUS_PARSING, null);
            Path file = Path.of(kbProperties.getDataDir(), doc.getId() + "." + doc.getFileType());
            if (!file.toFile().exists()) {
                throw new IllegalStateException("文件不存在: " + file);
            }

            // 1. 解析 + 分片
            List<Chunk> chunks = documentParser.parse(file, doc.getFileType(), doc.getFileName());
            if (chunks.isEmpty()) {
                // 空文档视为完成（无可入库内容）
                doc.setChunkCount(0);
                updateStatus(doc, STATUS_DONE, null);
                return;
            }

            // 2. 向量化（批量）
            updateStatus(doc, STATUS_EMBEDDING, null);
            List<String> texts = chunks.stream().map(Chunk::text).toList();
            List<double[]> vectors = embeddingService.embedInBatches(texts);

            // 3. 写 ES（docId 为 knowledge_doc.id，便于按文档删除；切片带 owner 归属，spec §4.4）
            List<KbChunkDoc> esDocs = new ArrayList<>();
            for (int i = 0; i < chunks.size(); i++) {
                Chunk c = chunks.get(i);
                esDocs.add(new KbChunkDoc(c.text(), vectors.get(i),
                        doc.getFileName(), doc.getFileType(), c.metadataJson(),
                        doc.getKbId(), doc.getOwnerType(), doc.getOwnerId(), doc.getCourseId(),
                        String.valueOf(doc.getId()), i));
            }
            esKnowledge.addDocuments(esDocs);

            // 4. 更新状态与计数
            doc.setChunkCount(esDocs.size());
            updateStatus(doc, STATUS_DONE, null);
            KnowledgeBase kb = kbMapper.selectById(doc.getKbId());
            if (kb != null) {
                kb.setChunkCount((kb.getChunkCount() == null ? 0 : kb.getChunkCount()) + esDocs.size());
                kbMapper.updateById(kb);
            }
            log.info("文档入库完成 docId={} chunks={}", docId, esDocs.size());
        } catch (Exception e) {
            log.error("文档处理失败 docId={}", docId, e);
            String msg = e.getMessage();
            updateStatus(doc, STATUS_FAILED, msg == null ? "未知错误" : msg.substring(0, Math.min(500, msg.length())));
        }
    }

    private void updateStatus(KnowledgeDoc doc, int status, String errorMsg) {
        doc.setStatus(status);
        doc.setErrorMsg(errorMsg);
        docMapper.updateById(doc);
    }
}
