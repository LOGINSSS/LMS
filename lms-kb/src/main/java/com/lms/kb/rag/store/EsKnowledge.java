package com.lms.kb.rag.store;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch._types.KnnSearch;
import co.elastic.clients.elasticsearch.core.BulkRequest;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.bulk.BulkOperation;
import com.lms.kb.config.KbProperties;
import com.lms.kb.ingest.EmbeddingService;
import com.lms.kb.knowledge.domain.es.KbChunkDoc;
import com.lms.kb.rag.domain.RagChunk;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * ES 向量存储与混合检索（对应 Python 版 store.py 的 VectorStore）
 *
 * - 索引 lms_kb_chunk：text(BM25) + embedding(dense_vector/COSINE) + owner_type/owner_id/course_id(过滤)
 * - 混合检索：query 一路(BM25 match text) + 顶层 knn 一路(KnnSearch) + rank:rrf 融合
 * - 归属隔离（spec §4.4）：owner_type=1 → 按 course_id 过滤（兼容旧数据）；
 *   owner_type=2 → 按 owner_type+owner_id 过滤，实现「每用户一个个人知识库」
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EsKnowledge {

    /** 归属类型：1 课程（旧语义，course_id 过滤） */
    public static final int OWNER_COURSE = 1;
    /** 归属类型：2 用户（个人知识库） */
    public static final int OWNER_USER = 2;

    private static final List<String> RETURN_FIELDS =
            List.of("text", "source", "doc_type", "metadata", "kb_id", "owner_type", "owner_id", "course_id", "doc_id", "chunk_seq");

    private final ElasticsearchClient es;
    private final KbProperties props;
    private final EmbeddingService embeddingService;

    /** 幂等建索引（不存在才创建） */
    public void ensureIndex() throws IOException {
        boolean exists = es.indices().exists(e -> e.index(props.getEsIndex())).value();
        if (exists) {
            return;
        }
        es.indices().create(c -> c.index(props.getEsIndex())
                .mappings(m -> m
                        .properties("text", p -> p.text(t -> t.analyzer(props.getAnalyzer())))
                        .properties("embedding", p -> p.denseVector(d -> d
                                .dims(props.getDenseDim())
                                .index(true)
                                .similarity("cosine")))
                        .properties("source", p -> p.keyword(k -> k))
                        .properties("doc_type", p -> p.keyword(k -> k))
                        .properties("metadata", p -> p.keyword(k -> k))
                        .properties("kb_id", p -> p.long_(l -> l))
                        .properties("owner_type", p -> p.integer(i -> i))
                        .properties("owner_id", p -> p.long_(l -> l))
                        .properties("course_id", p -> p.long_(l -> l))
                        .properties("doc_id", p -> p.keyword(k -> k))
                        .properties("chunk_seq", p -> p.integer(i -> i))));
        log.info("已创建 ES 索引: {}", props.getEsIndex());
    }

    /** 批量写入切片（文档 id = docId_chunkSeq，按 docId 可整体删除） */
    public void addDocuments(List<KbChunkDoc> docs) throws IOException {
        ensureIndex();
        if (docs.isEmpty()) {
            return;
        }
        BulkRequest.Builder br = new BulkRequest.Builder();
        for (KbChunkDoc d : docs) {
            br.operations(o -> o.index(i -> i
                    .index(props.getEsIndex())
                    .id(d.docId() + "_" + d.chunkSeq())
                    .document(d)));
        }
        es.bulk(br.build());
    }

    /**
     * 混合检索（按归属）：owner_type=1 按 course_id 过滤（兼容旧数据）；owner_type=2 按 owner 过滤。
     * query(BM25 match text) + knn(embedding)，RRF 融合。
     */
    public List<RagChunk> hybridSearch(String query, Integer ownerType, Long ownerId, int topK) throws IOException {
        ensureIndex();
        double[] vec = embeddingService.embedQuery(query);
        int fetchK = props.getFetchK();
        boolean userKb = ownerType != null && ownerType == OWNER_USER;

        // 归属过滤条件（knn 与 BM25 共用）
        List<co.elastic.clients.elasticsearch._types.query_dsl.Query> filters = new ArrayList<>();
        if (userKb) {
            filters.add(co.elastic.clients.elasticsearch._types.query_dsl.Query.of(q ->
                    q.term(t -> t.field("owner_type").value(FieldValue.of(OWNER_USER)))));
            filters.add(co.elastic.clients.elasticsearch._types.query_dsl.Query.of(q ->
                    q.term(t -> t.field("owner_id").value(FieldValue.of(ownerId)))));
        } else {
            filters.add(co.elastic.clients.elasticsearch._types.query_dsl.Query.of(q ->
                    q.term(t -> t.field("course_id").value(FieldValue.of(ownerId)))));
        }

        // knn 一路（embedding + 归属 filter）
        KnnSearch knn = new KnnSearch.Builder()
                .field("embedding")
                .queryVector(Arrays.stream(vec).boxed().map(Double::floatValue).toList())
                .k((long) fetchK)
                .numCandidates((long) fetchK * 10)
                .filter(f -> f.bool(b -> {
                    filters.forEach(b::must);
                    return b;
                }))
                .build();

        // query 一路（BM25 match text + 归属 filter），与 knn 由 rank:rrf 融合
        SearchRequest req = new SearchRequest.Builder()
                .index(props.getEsIndex())
                .query(q -> q.bool(b -> {
                    filters.forEach(b::must);
                    b.must(m -> m.match(mt -> mt.field("text").query(query)));
                    return b;
                }))
                .knn(knn)
                .rank(r -> r.rrf(rr -> rr.rankConstant((long) props.getRrfK())))
                .size(topK)
                .source(s -> s.filter(f -> f.includes(RETURN_FIELDS)))
                .build();

        SearchResponse<KbChunkDoc> resp = es.search(req, KbChunkDoc.class);
        List<RagChunk> out = new ArrayList<>();
        for (var hit : resp.hits().hits()) {
            KbChunkDoc src = hit.source();
            if (src == null) {
                continue;
            }
            out.add(new RagChunk(src.text(), src.source(), src.docType(), src.metadata(),
                    hit.score() == null ? 0 : hit.score()));
        }
        return out;
    }

    /** 兼容重载：课程知识库检索（ownerType=1，按 course_id 过滤） */
    public List<RagChunk> hybridSearch(String query, Long courseId, int topK) throws IOException {
        return hybridSearch(query, OWNER_COURSE, courseId, topK);
    }

    /** 删除某文档的全部切片（对应 delete_by_doc_id） */
    public void deleteByDocId(String docId) throws IOException {
        ensureIndex();
        es.deleteByQuery(d -> d.index(props.getEsIndex())
                .query(q -> q.term(t -> t.field("doc_id").value(FieldValue.of(docId)))));
    }

    /** 删除某课程的全部切片（删除课程知识库时用，兼容旧语义） */
    public void deleteByCourseId(Long courseId) throws IOException {
        ensureIndex();
        es.deleteByQuery(d -> d.index(props.getEsIndex())
                .query(q -> q.term(t -> t.field("course_id").value(FieldValue.of(courseId)))));
    }

    /** 删除某归属的全部切片（删除知识库时用，spec §4.4：个人库按 owner 删） */
    public void deleteByOwner(Integer ownerType, Long ownerId) throws IOException {
        ensureIndex();
        if (ownerType != null && ownerType == OWNER_USER) {
            es.deleteByQuery(d -> d.index(props.getEsIndex())
                    .query(q -> q.bool(b -> b
                            .must(m -> m.term(t -> t.field("owner_type").value(FieldValue.of(OWNER_USER))))
                            .must(m -> m.term(t -> t.field("owner_id").value(FieldValue.of(ownerId)))))));
        } else {
            deleteByCourseId(ownerId);
        }
    }
}
