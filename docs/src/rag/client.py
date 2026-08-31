"""RAGClient：把整套 RAG 架构封装成可编程 SDK 的门面类。

用法：
    from rag import RAGClient

    # 1) 默认：从环境变量读取配置（与 .env 变量同名，可单独覆盖）
    with RAGClient() as client:
        client.ingest_text("Milvus 是一个开源向量数据库…", source="notes.txt")
        result = client.ask("Milvus 是什么？")
        print(result["answer"])

    # 2) 显式配置 / 多实例（不同集合、不同 API key 互不干扰）
    from rag.settings import Settings
    c1 = RAGClient(Settings(MILVUS_COLLECTION="kb_a", DEEPSEEK_API_KEY="..."))
    c2 = RAGClient(MILVUS_COLLECTION="kb_b")          # 关键字参数 = Settings 字段

    # 3) 复用本项目（FastAPI 服务同一份数据/集合）的配置
    c3 = RAGClient.from_project_config()

所有方法要么调用注入到本实例的模型运行时 / 向量库 / 文件注册表，
要么走模块级默认（环境配置），与现有 FastAPI 服务行为一致。
"""
from __future__ import annotations

from dataclasses import replace
from pathlib import Path
from typing import Optional

from . import config, graph, ingest
from .files import FileRegistry
from .llm import LLMRuntime
from .settings import Settings
from .store import VectorStore


class RAGClient:
    """多模态 RAG SDK 门面：问答 / 入库 / 知识库管理一站式入口。"""

    def __init__(self, settings: Optional[Settings] = None, **overrides):
        """settings 为运行时配置；overrides 以 Settings 字段名覆盖（大写，同 .env）。"""
        base = settings if settings is not None else Settings()
        if overrides:
            base = replace(base, **overrides)
        self.settings: Settings = base
        self._llm: Optional[LLMRuntime] = None
        self._store: Optional[VectorStore] = None
        self._files: Optional[FileRegistry] = None
        self._graph = None

    # ---------- 组件（惰性构建，绑定本实例 settings） ----------

    @property
    def llm(self) -> LLMRuntime:
        """模型运行时：DeepSeek R1/chat + DashScope embedding/Qwen-VL/rerank。"""
        if self._llm is None:
            self._llm = LLMRuntime(self.settings)
        return self._llm

    @property
    def store(self) -> VectorStore:
        """Milvus 向量库（BM25 + 稠密混合检索）。"""
        if self._store is None:
            self._store = VectorStore(self.settings, embeddings=self.llm.embeddings)
        return self._store

    @property
    def files(self) -> FileRegistry:
        """文件注册表（原始文件 + 元数据索引，位于 settings.DATA_DIR）。"""
        if self._files is None:
            self._files = FileRegistry(self.settings.DATA_DIR)
        return self._files

    @property
    def graph(self):
        """LangGraph 问答图（改写→HyDE→检索→精排→生成）。"""
        if self._graph is None:
            self._graph = graph.build_graph(runtime=self.llm, store_=self.store)
        return self._graph

    # ---------- 问答 ----------

    def ask(self, question: str, callbacks: list | None = None) -> dict:
        """执行一次完整 RAG 问答，返回
        {"answer", "sources", "rewritten", "hyde", ...}。

        callbacks: 可选回调列表（如 LangFuse CallbackHandler），用于观测 trace。
        """
        config_dict = {"callbacks": callbacks} if callbacks else None
        return self.graph.invoke({"question": question}, config=config_dict)

    # ---------- 入库 ----------

    def ingest_file(
        self,
        path: str | Path,
        source: str | None = None,
        doc_id: str | None = None,
        progress_cb=None,
    ) -> int:
        """入库文档/图片（md/txt/docx/pptx/pdf/png/jpg/...），返回写入的 chunk 数。

        source 为显示用文件名（默认取 path.name）；doc_id 为文件唯一 id；
        progress_cb(fraction: 0~1) 可选进度回调。
        """
        return ingest.ingest_file(
            path, source=source, doc_id=doc_id, progress_cb=progress_cb,
            runtime=self.llm, store=self.store, settings=self.settings,
        )

    def ingest_text(self, text: str, source: str = "inline", doc_type: str = "text") -> int:
        """直接入库一段文本，返回写入的 chunk 数。"""
        return ingest.ingest_text(
            text, source=source, doc_type=doc_type,
            runtime=self.llm, store=self.store, settings=self.settings,
        )

    def ingest_image(
        self,
        path: str | Path,
        source: str | None = None,
        doc_id: str | None = None,
        progress_cb=None,
    ) -> int:
        """入库图片（OCR + VLM 描述），返回写入的 chunk 数。"""
        return ingest.ingest_image(
            path, source=source, doc_id=doc_id, progress_cb=progress_cb,
            runtime=self.llm, store=self.store, settings=self.settings,
        )

    # ---------- 知识库管理 ----------

    def list_files(self) -> list:
        """知识库文件列表（名称/类型/chunk数/时间/状态）。"""
        return self.files.all_files()

    def delete(self, doc_id: str) -> int:
        """删除 doc_id 对应的全部向量 + 原始文件 + 注册记录，返回删除的向量条数。"""
        n = self.store.delete_by_doc_id(doc_id)
        entry = self.files.get(doc_id)
        if entry:
            self.files.final_path(doc_id, entry["filename"]).unlink(missing_ok=True)
            self.files.pop(doc_id)
        return n

    def download_path(self, doc_id: str) -> Optional[Path]:
        """返回 doc_id 原始文件的本地路径（不存在返回 None）。"""
        entry = self.files.get(doc_id)
        if not entry:
            return None
        p = self.files.final_path(doc_id, entry["filename"])
        return p if p.exists() else None

    def count(self) -> int:
        """当前向量库条数。"""
        return self.store.count()

    def health(self) -> dict:
        """健康检查：Milvus 连通性 + 条数。"""
        try:
            self.store.ensure_collection()
            milvus_ok, rows, err = True, self.store.count(), ""
        except Exception as e:  # noqa: BLE001
            milvus_ok, rows, err = False, 0, str(e)
        return {
            "status": "ok" if milvus_ok else "degraded",
            "milvus_ok": milvus_ok,
            "rows": rows,
            "error": err,
        }

    # ---------- 生命周期 ----------

    def close(self) -> None:
        """释放 Milvus 连接等资源（后续再使用会自动按 settings 重建）。"""
        if self._store is not None:
            self._store.release_client()
        self._store = None

    def __enter__(self) -> "RAGClient":
        return self

    def __exit__(self, *exc) -> None:
        self.close()

    # ---------- 工厂 ----------

    @classmethod
    def from_project_config(cls) -> "RAGClient":
        """使用本项目（src/rag 所在仓库）的 .env 配置：与 FastAPI 服务
        共用同一数据目录（data/）与 Milvus 集合。"""
        return cls(config.DEFAULT_SETTINGS)
