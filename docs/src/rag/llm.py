"""模型客户端：DeepSeek（R1/chat）、DashScope（embedding + Qwen-VL + rerank）。

两种用法（SDK 化后）：

1. **模块级单例（向后兼容）**：`from . import llm`，再 `llm.rewrite_llm` /
   `llm.embeddings` / `llm.generator`。惰性构建，首次真正调用时才创建连接对象，
   没配 API key 时 `import rag` 也不会在 import 阶段抛错。

2. **实例（SDK 多实例）**：`llm.build_runtime(settings)` 得到 LLMRuntime，
   每个实例绑定自己的 Settings（API key / 模型名），互不干扰。
"""
from __future__ import annotations

from typing import List, Optional

import dashscope
from dashscope import TextEmbedding, TextReRank
from langchain_deepseek import ChatDeepSeek
from langchain_openai import ChatOpenAI

from . import config
from .settings import Settings

_CACHE: dict = {}


def _build_rewrite_llm(settings: Optional[Settings] = None):
    s = settings or config
    # R1：query 重构（推理能力强，慢一点没关系）
    return ChatDeepSeek(
        model=s.DEEPSEEK_REASONER_MODEL,
        api_key=s.DEEPSEEK_API_KEY,
        temperature=0,
        timeout=120,
    )


def _build_chat_llm(settings: Optional[Settings] = None):
    s = settings or config
    # chat：HyDE 等便宜、快的小任务
    return ChatDeepSeek(
        model=s.DEEPSEEK_CHAT_MODEL,
        api_key=s.DEEPSEEK_API_KEY,
        temperature=0.7,
        timeout=60,
    )


class _DashScopeEmbeddings:
    """DashScope text-embedding-v3 原生接口封装。

    提供 langchain 兼容的 embed_documents / embed_query。
    用原生 TextEmbedding（input.texts）而非 OpenAI 兼容端点，
    因为兼容端点对 text-embedding-v3 会报 400（contents 字段不匹配）。
    """

    def __init__(self, model: str, api_key: str):
        self.model = model
        self.api_key = api_key

    # DashScope text-embedding 单次请求最多 10 条
    _BATCH_SIZE = 10

    def _embed(self, texts: List[str], progress_cb=None) -> List[List[float]]:
        # 分批调用，避免单次超过 DashScope 的 batch 上限（>10 报 400）
        out: List[List[float]] = []
        total = len(texts)
        done = 0
        for i in range(0, total, self._BATCH_SIZE):
            batch = list(texts[i : i + self._BATCH_SIZE])
            resp = TextEmbedding.call(
                model=self.model,
                input=batch,
                api_key=self.api_key,
            )
            if resp.status_code != 200:  # type: ignore[attr-defined]
                raise RuntimeError(f"embedding 失败: {resp.message}")  # type: ignore[attr-defined]
            out.extend(e["embedding"] for e in resp.output["embeddings"])  # type: ignore[attr-defined]
            done += len(batch)
            if progress_cb and total:
                progress_cb(done / total)
        return out

    def embed_documents(self, texts: List[str], progress_cb=None) -> List[List[float]]:
        return self._embed(texts, progress_cb)

    def embed_query(self, text: str) -> List[float]:
        return self._embed([text])[0]


def _build_embeddings(settings: Optional[Settings] = None):
    s = settings or config
    # DashScope text-embedding-v3（原生 TextEmbedding 接口）
    return _DashScopeEmbeddings(
        model=s.DASHSCOPE_EMBEDDING_MODEL,
        api_key=s.DASHSCOPE_API_KEY,
    )


def _build_generator(settings: Optional[Settings] = None):
    s = settings or config
    # Qwen-VL 生成（多模态，OpenAI 兼容端点）
    return ChatOpenAI(
        model=s.QWEN_VL_MODEL,
        base_url=s.DASHSCOPE_BASE_URL,
        api_key=s.DASHSCOPE_API_KEY,
        temperature=0,
        timeout=120,
    )


_FACTORIES = {
    "rewrite_llm": _build_rewrite_llm,
    "chat_llm": _build_chat_llm,
    "embeddings": _build_embeddings,
    "generator": _build_generator,
}


def __getattr__(name: str):
    if name in _FACTORIES:
        if name not in _CACHE:
            _CACHE[name] = _FACTORIES[name]()
        return _CACHE[name]
    raise AttributeError(f"module {__name__!r} has no attribute {name!r}")


class LLMRuntime:
    """一组绑定同一份 Settings 的模型客户端（惰性构建，SDK 多实例用）。

    鸭子类型与模块级单例一致：有 rewrite_llm / chat_llm / embeddings /
    generator 属性与 rerank() 方法，可直接传给 graph.build_graph(runtime=...)。
    """

    def __init__(self, settings: Optional[Settings] = None):
        self.settings = settings or Settings()
        self._cache: dict = {}

    def _get(self, name: str):
        if name not in self._cache:
            self._cache[name] = _FACTORIES[name](self.settings)
        return self._cache[name]

    @property
    def rewrite_llm(self):
        return self._get("rewrite_llm")

    @property
    def chat_llm(self):
        return self._get("chat_llm")

    @property
    def embeddings(self):
        return self._get("embeddings")

    @property
    def generator(self):
        return self._get("generator")

    def rerank(self, query: str, documents: List[str], top_n: int) -> List[int]:
        return rerank(query, documents, top_n, settings=self.settings)


def build_runtime(settings: Optional[Settings] = None) -> LLMRuntime:
    """构建一个独立模型运行时（SDK 多实例用）。"""
    return LLMRuntime(settings)


# ---------- DashScope rerank 精排 ----------
def rerank(
    query: str,
    documents: List[str],
    top_n: int,
    settings: Optional[Settings] = None,
) -> List[int]:
    """对 documents 精排，返回按相关度降序的「原下标」列表。

    用 DashScope 的 rerank 模型（默认 gte-rerank-v2）。
    想换本地 Qwen3-Reranker / bge-reranker 时，只改这个函数即可。
    """
    if not documents:
        return []
    s = settings or config
    resp: TextReRank = TextReRank.call(
        model=s.RERANK_MODEL,
        query=query,
        documents=documents,
        top_n=min(top_n, len(documents)),
        api_key=s.DASHSCOPE_API_KEY,
    )
    if resp.status_code != 200:  # type: ignore[attr-defined]
        raise RuntimeError(f"rerank 失败: {resp.message}")  # type: ignore[attr-defined]

    results = resp.output.results  # type: ignore[attr-defined]
    ordered = sorted(results, key=lambda r: r.relevance_score, reverse=True)
    return [r.index for r in ordered]
