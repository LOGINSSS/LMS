"""Milvus 向量库：建集合 + 混合检索（BM25 全文 + 稠密向量，RRF 融合）。

用 pymilvus 的 MilvusClient 直接操作，BM25 走 Milvus 2.5+ 的全文检索：
text 字段 enable_analyzer=True + BM25 函数生成稀疏向量字段 sparse_bm25，
混合检索时 BM25 一路检索 sparse_bm25（不能直接在 VARCHAR 字段上搜）。

两种用法：
1. 模块级函数（向后兼容）：`store.ensure_collection()` / `store.hybrid_search(...)`
   —— 内部用「默认 VectorStore」（绑定环境配置）。
2. SDK 实例：`VectorStore(settings, embeddings=...)`，每个实例独立的
   Milvus 连接 / 集合名 / embedding 客户端。
"""
from __future__ import annotations

import logging
from typing import Dict, List, Optional

from pymilvus import AnnSearchRequest, DataType, Function, FunctionType, MilvusClient, RRFRanker

from . import config, llm
from .settings import Settings

logger = logging.getLogger(__name__)


class VectorStore:
    """一个 Milvus 集合的操作句柄（绑定 Settings，惰性连接）。

    embeddings 可选注入（通常是 LLMRuntime.embeddings）；
    不传时用模块级 llm.embeddings（环境配置），保持向后兼容。
    """

    def __init__(self, settings: Optional[Settings] = None, embeddings=None):
        self.settings = settings or Settings()
        self._client: Optional[MilvusClient] = None
        self._embeddings = embeddings

    # ---------- 连接 ----------

    def get_client(self) -> MilvusClient:
        # 懒连接：MilvusClient 构造时就会连服务器，没起 Docker 时会抛异常，
        # 所以首次真正使用时才建连接。
        if self._client is None:
            self._client = MilvusClient(uri=self.settings.MILVUS_URI)
        return self._client

    def release_client(self) -> None:
        """释放连接（close() 后若再次使用会按 settings 重建）。"""
        if self._client is not None:
            try:
                self._client.close()
            except Exception:  # noqa: BLE001
                pass
            self._client = None

    def _embeddings_impl(self):
        if self._embeddings is not None:
            return self._embeddings
        return llm.embeddings  # 模块级单例（环境配置），向后兼容

    # ---------- 集合结构 ----------

    def _build_bm25_schema(self, client: MilvusClient):
        """构造带 BM25 函数（text -> sparse_bm25 稀疏向量）的集合 schema。"""
        bm25_function = Function(
            name="bm25",
            function_type=FunctionType.BM25,
            input_field_names=["text"],
            output_field_names=["sparse_bm25"],
        )
        schema = client.create_schema(auto_id=True, enable_dynamic_field=False, functions=[bm25_function])
        schema.add_field("pk", DataType.INT64, is_primary=True)
        schema.add_field(
            "text",
            DataType.VARCHAR,
            max_length=65535,
            enable_analyzer=True,
            analyzer_params={"type": "chinese"},
        )
        schema.add_field("sparse_bm25", DataType.SPARSE_FLOAT_VECTOR)
        schema.add_field("dense", DataType.FLOAT_VECTOR, dim=self.settings.DENSE_DIM)
        schema.add_field("source", DataType.VARCHAR, max_length=512, nullable=True)
        schema.add_field("doc_id", DataType.VARCHAR, max_length=32, nullable=True)
        schema.add_field("doc_type", DataType.VARCHAR, max_length=32, nullable=True)
        schema.add_field("metadata", DataType.VARCHAR, max_length=2048, nullable=True)
        return schema

    def _create_indexes(self, client: MilvusClient) -> None:
        """dense(COSINE) + sparse_bm25(BM25) 双索引。"""
        dense_index = client.prepare_index_params()
        dense_index.add_index(field_name="dense", metric_type="COSINE", index_type="AUTOINDEX")
        client.create_index(self.settings.MILVUS_COLLECTION, index_params=dense_index)

        bm25_index = client.prepare_index_params()
        bm25_index.add_index(field_name="sparse_bm25", metric_type="BM25", index_type="SPARSE_INVERTED_INDEX")
        client.create_index(self.settings.MILVUS_COLLECTION, index_params=bm25_index)

    def _migrate_legacy_collection(self, client: MilvusClient) -> None:
        """旧结构集合（缺 sparse_bm25 字段）无损迁移：导出 → 重建 → 回填。

        Milvus 2.5 不支持 AlterCollectionSchema，无法原地给集合加函数字段，
        只能重建；重建前先把旧数据（text/dense/元数据）导出，重建后回填。
        """
        name = self.settings.MILVUS_COLLECTION
        client.load_collection(name)
        # Milvus 单次查询 limit 上限 16384，分批拉取旧数据
        rows: List[Dict] = []
        offset = 0
        BATCH = 16000
        while True:
            batch = client.query(
                name,
                filter="pk >= 0",
                output_fields=["text", "dense", "source", "doc_type", "metadata"],
                limit=BATCH,
                offset=offset,
            )
            if not batch:
                break
            rows.extend(batch)
            offset += len(batch)
            if len(batch) < BATCH:
                break
        client.drop_collection(name)
        client.create_collection(name, schema=self._build_bm25_schema(client))
        self._create_indexes(client)
        data = [
            {
                "text": r["text"],
                "dense": r["dense"],
                "source": r.get("source"),
                "doc_type": r.get("doc_type"),
                "metadata": r.get("metadata"),
            }
            for r in rows
        ]
        if data:
            client.insert(name, data=data)
        client.load_collection(name)

    def ensure_collection(self) -> None:
        """建集合（若不存在）并建索引。幂等，可重复调用。

        若发现旧结构集合（缺 sparse_bm25 或 doc_id 字段），自动做无损迁移。
        """
        client = self.get_client()
        if client.has_collection(self.settings.MILVUS_COLLECTION):
            desc = client.describe_collection(self.settings.MILVUS_COLLECTION)
            fields = {f["name"] for f in desc.get("fields", [])}
            if "sparse_bm25" not in fields or "doc_id" not in fields:
                logger.warning("检测到旧结构集合（缺 sparse_bm25/doc_id 字段），执行无损迁移…")
                self._migrate_legacy_collection(client)
            return

        client.create_collection(self.settings.MILVUS_COLLECTION, schema=self._build_bm25_schema(client))
        self._create_indexes(client)
        client.load_collection(self.settings.MILVUS_COLLECTION)

    # ---------- 读写 ----------

    def insert(self, chunks: List[Dict]) -> int:
        """插入 chunks，返回插入条数。chunk 形如
        {"text": str, "dense": List[float], "source": str, "doc_type": str, "metadata": str}
        """
        self.ensure_collection()
        if not chunks:
            return 0
        client = self.get_client()
        res = client.insert(self.settings.MILVUS_COLLECTION, data=chunks)
        return res.get("insert_count", len(chunks))

    def hybrid_search(
        self,
        query: str,
        top_k: int = 5,
        fetch_k: Optional[int] = None,
        expr: Optional[str] = None,
    ) -> List[Dict]:
        """混合检索：BM25 + 稠密向量，RRF 融合，返回 top_k 条。

        返回 [{"text": str, "source": str, "doc_type": str, "metadata": str, "score": float}]
        """
        self.ensure_collection()
        fetch_k = fetch_k or self.settings.HYBRID_FETCH_K
        client = self.get_client()

        # 一路：BM25 全文检索（搜 BM25 函数生成的稀疏向量字段）
        bm25_req = AnnSearchRequest(
            data=[query],
            anns_field="sparse_bm25",
            param={"metric_type": "BM25"},
            limit=fetch_k,
            expr=expr,
        )
        # 一路：稠密向量检索
        dense_vec = self._embeddings_impl().embed_query(query)
        dense_req = AnnSearchRequest(
            data=[dense_vec],
            anns_field="dense",
            param={"metric_type": "COSINE", "params": {"nprobe": 16}},
            limit=fetch_k,
            expr=expr,
        )

        results = client.hybrid_search(
            self.settings.MILVUS_COLLECTION,
            reqs=[bm25_req, dense_req],
            ranker=RRFRanker(k=self.settings.RRF_K),
            limit=top_k,
            output_fields=["text", "source", "doc_type", "metadata"],
        )

        out: List[Dict] = []
        for hit in results[0]:
            entity = hit["entity"]
            out.append(
                {
                    "text": entity["text"],
                    "source": entity.get("source", ""),
                    "doc_type": entity.get("doc_type", ""),
                    "metadata": entity.get("metadata", ""),
                    "score": hit["distance"],
                }
            )
        return out

    def count(self) -> int:
        """集合里的向量条数。"""
        client = self.get_client()
        if not client.has_collection(self.settings.MILVUS_COLLECTION):
            return 0
        stats = client.get_collection_stats(self.settings.MILVUS_COLLECTION)
        return stats.get("row_count", 0)

    def count_by_doc_id(self, doc_id: str) -> int:
        """统计指定 doc_id 的 chunks 数。"""
        client = self.get_client()
        if not client.has_collection(self.settings.MILVUS_COLLECTION):
            return 0
        esc = doc_id.replace("\\", "\\\\").replace('"', '\\"')
        res = client.query(
            self.settings.MILVUS_COLLECTION,
            filter=f'doc_id == "{esc}"',
            output_fields=["pk"],
            consistency_level="Strong",
        )
        return len(res)

    def delete_by_doc_id(self, doc_id: str) -> int:
        """删除指定 doc_id 的全部 chunks，返回删除条数。"""
        client = self.get_client()
        if not client.has_collection(self.settings.MILVUS_COLLECTION):
            return 0
        esc = doc_id.replace("\\", "\\\\").replace('"', '\\"')
        res = client.delete(self.settings.MILVUS_COLLECTION, filter=f'doc_id == "{esc}"')
        return res.get("delete_count", 0)


# ---------- 模块级函数（向后兼容）：委托给默认实例 ----------

_DEFAULT_STORE: Optional[VectorStore] = None


def _default_store() -> VectorStore:
    global _DEFAULT_STORE
    if _DEFAULT_STORE is None:
        # settings=None → 环境配置；embeddings=None → 模块级 llm.embeddings
        _DEFAULT_STORE = VectorStore()
    return _DEFAULT_STORE


def get_client() -> MilvusClient:
    return _default_store().get_client()


def ensure_collection() -> None:
    _default_store().ensure_collection()


def insert(chunks: List[Dict]) -> int:
    return _default_store().insert(chunks)


def hybrid_search(
    query: str,
    top_k: int = 5,
    fetch_k: Optional[int] = None,
    expr: Optional[str] = None,
) -> List[Dict]:
    return _default_store().hybrid_search(query, top_k=top_k, fetch_k=fetch_k, expr=expr)


def count() -> int:
    return _default_store().count()


def count_by_doc_id(doc_id: str) -> int:
    return _default_store().count_by_doc_id(doc_id)


def delete_by_doc_id(doc_id: str) -> int:
    return _default_store().delete_by_doc_id(doc_id)
