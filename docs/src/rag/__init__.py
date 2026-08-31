"""RAG：LangGraph 编排的多模态检索增强生成系统（SDK + FastAPI 服务 + CLI）。

SDK 用法：
    from rag import RAGClient, Settings
    with RAGClient(Settings(MILVUS_COLLECTION="kb_a")) as client:
        client.ingest_text("...", source="notes.txt")
        print(client.ask("...")["answer"])

RAGClient / Settings 为懒加载导出：`import rag` 本身保持轻量，
首次访问时才会导入底层依赖（pymilvus / langgraph 等）。
"""
from .cli import main

__all__ = ["main", "RAGClient", "Settings"]


def __getattr__(name: str):
    if name in {"RAGClient", "Settings"}:
        from .client import RAGClient
        from .settings import Settings

        globals()[name] = locals()[name]
        return globals()[name]
    raise AttributeError(f"module {__name__!r} has no attribute {name!r}")
