"""SDK 运行时配置：Settings 数据类。

与 config.py 的关系：
- config.py 是**向后兼容的模块级全局配置**（import 时从项目根目录 .env 读取），
  供现有 FastAPI 服务 / CLI 使用，行为不变。
- Settings 是 **RAGClient 的运行时配置对象**，每个 RAGClient 实例一份，
  支持多实例（不同 API key / Milvus 集合 / 数据目录互不干扰）。

字段名与 .env / config.py 常量**一致（大写）**，因此任何函数都可以写
`s = settings or config` 来同时接受 Settings 对象与旧 config 模块。

注意：默认值用 default_factory 在**实例化时**读取环境变量，
避免「先 import settings 再 load_dotenv 导致默认值丢失」的问题。
"""
from __future__ import annotations

import os
from dataclasses import dataclass, field
from pathlib import Path
from typing import Callable


def _env_str(name: str, default: str = "") -> Callable[[], str]:
    """返回一个 default_factory：实例化时读取环境变量。"""

    def _factory() -> str:
        return os.getenv(name, default)

    return _factory


def _env_int(name: str, default: int) -> Callable[[], int]:
    def _factory() -> int:
        raw = os.getenv(name)
        try:
            return int(raw) if raw else default
        except ValueError:
            return default

    return _factory


def _env_path(name: str, default: Path) -> Callable[[], Path]:
    def _factory() -> Path:
        raw = os.getenv(name)
        return Path(raw) if raw else default

    return _factory


@dataclass
class Settings:
    """RAGClient 的运行时配置。字段名与 .env 变量一致（大写）。

    用法：
        s = Settings()                                   # 从环境变量读取
        s = Settings(MILVUS_URI="http://x:19530")        # 覆盖单个字段
        s = replace(s, DEEPSEEK_API_KEY="sk-...")        # 基于已有对象覆盖
    """

    # ---------- 目录 ----------
    DATA_DIR: Path = field(
        default_factory=_env_path("RAG_DATA_DIR", Path.home() / ".rag-sdk" / "data")
    )

    # ---------- API key ----------
    DEEPSEEK_API_KEY: str = field(default_factory=_env_str("DEEPSEEK_API_KEY"))
    DASHSCOPE_API_KEY: str = field(default_factory=_env_str("DASHSCOPE_API_KEY"))

    # ---------- Milvus ----------
    MILVUS_URI: str = field(
        default_factory=_env_str("MILVUS_URI", "http://localhost:19530")
    )
    MILVUS_COLLECTION: str = field(
        default_factory=_env_str("MILVUS_COLLECTION", "rag_chunks")
    )
    DENSE_DIM: int = field(default_factory=_env_int("DENSE_DIM", 1024))

    # ---------- 模型名 ----------
    DEEPSEEK_REASONER_MODEL: str = field(
        default_factory=_env_str("DEEPSEEK_REASONER_MODEL", "deepseek-reasoner")
    )
    DEEPSEEK_CHAT_MODEL: str = field(
        default_factory=_env_str("DEEPSEEK_CHAT_MODEL", "deepseek-chat")
    )
    DASHSCOPE_EMBEDDING_MODEL: str = field(
        default_factory=_env_str("DASHSCOPE_EMBEDDING_MODEL", "text-embedding-v3")
    )
    QWEN_VL_MODEL: str = field(default_factory=_env_str("QWEN_VL_MODEL", "qwen-vl-max"))
    RERANK_MODEL: str = field(default_factory=_env_str("RERANK_MODEL", "gte-rerank-v2"))

    # DashScope 的 OpenAI 兼容端点（embedding + Qwen-VL 都走这里）
    DASHSCOPE_BASE_URL: str = "https://dashscope.aliyuncs.com/compatible-mode/v1"

    # ---------- 可选：LangFuse ----------
    LANGFUSE_PUBLIC_KEY: str = field(default_factory=_env_str("LANGFUSE_PUBLIC_KEY"))
    LANGFUSE_SECRET_KEY: str = field(default_factory=_env_str("LANGFUSE_SECRET_KEY"))
    LANGFUSE_HOST: str = field(default_factory=_env_str("LANGFUSE_HOST"))

    # ---------- MinerU（PDF 版面解析） ----------
    MINERU_MODEL_SOURCE: str = field(
        default_factory=_env_str("MINERU_MODEL_SOURCE", "modelscope")
    )
    MINERU_BACKEND: str = field(default_factory=_env_str("MINERU_BACKEND", "pipeline"))
    MINERU_METHOD: str = field(default_factory=_env_str("MINERU_METHOD", "auto"))
    MINERU_LANG: str = field(default_factory=_env_str("MINERU_LANG", "ch"))
    MINERU_TIMEOUT: int = field(default_factory=_env_int("MINERU_TIMEOUT", 600))

    # ---------- 检索参数 ----------
    HYBRID_FETCH_K: int = 20   # 每一路检索预取的候选数
    RERANK_TOP_K: int = 5      # 精排后保留的条数
    RRF_K: int = 60            # RRF 平滑常数

    @classmethod
    def from_env(cls) -> "Settings":
        """从环境变量构建（等价于 Settings()，语义更明确）。"""
        return cls()
