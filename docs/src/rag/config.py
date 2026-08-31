"""全局配置：从项目根目录的 .env 读取环境变量（向后兼容的模块级配置）。

SDK 用户请优先使用 `from rag.settings import Settings`（运行时配置对象），
或用 `RAGClient.from_project_config()` 复用本项目这份配置。
"""
from __future__ import annotations

import os
from dataclasses import replace
from pathlib import Path

from dotenv import load_dotenv

from .settings import Settings

# 项目根目录：src/rag/config.py -> 上三级到 RAG/
ROOT = Path(__file__).resolve().parents[2]

# 作为 SDK 安装进 site-packages 时，parents[2] 不再是项目根（没有 pyproject.toml），
# 回退到用户目录，避免在包目录里乱建 data/ 目录。
if not (ROOT / "pyproject.toml").exists():
    ROOT = Path.home() / ".rag-sdk"

# 加载 ROOT/.env（不存在则忽略，走系统环境变量）
load_dotenv(ROOT / ".env")

# ---------- 目录 ----------
DATA_DIR = ROOT / "data"          # 上传/临时文档落盘目录
DATA_DIR.mkdir(exist_ok=True)

# 本项目默认配置：= 环境变量 + 数据目录指向项目 data/（供 RAGClient.from_project_config 复用）
DEFAULT_SETTINGS = replace(Settings(), DATA_DIR=DATA_DIR)

# ---------- API key ----------
DEEPSEEK_API_KEY = DEFAULT_SETTINGS.DEEPSEEK_API_KEY
DASHSCOPE_API_KEY = DEFAULT_SETTINGS.DASHSCOPE_API_KEY

# ---------- Milvus ----------
MILVUS_URI = DEFAULT_SETTINGS.MILVUS_URI
MILVUS_COLLECTION = DEFAULT_SETTINGS.MILVUS_COLLECTION
DENSE_DIM = DEFAULT_SETTINGS.DENSE_DIM

# ---------- 模型名 ----------
DEEPSEEK_REASONER_MODEL = DEFAULT_SETTINGS.DEEPSEEK_REASONER_MODEL
DEEPSEEK_CHAT_MODEL = DEFAULT_SETTINGS.DEEPSEEK_CHAT_MODEL
DASHSCOPE_EMBEDDING_MODEL = DEFAULT_SETTINGS.DASHSCOPE_EMBEDDING_MODEL
QWEN_VL_MODEL = DEFAULT_SETTINGS.QWEN_VL_MODEL
RERANK_MODEL = DEFAULT_SETTINGS.RERANK_MODEL

# DashScope 的 OpenAI 兼容端点（embedding + Qwen-VL 都走这里）
DASHSCOPE_BASE_URL = DEFAULT_SETTINGS.DASHSCOPE_BASE_URL

# ---------- 可选：LangFuse ----------
LANGFUSE_PUBLIC_KEY = DEFAULT_SETTINGS.LANGFUSE_PUBLIC_KEY
LANGFUSE_SECRET_KEY = DEFAULT_SETTINGS.LANGFUSE_SECRET_KEY
LANGFUSE_HOST = DEFAULT_SETTINGS.LANGFUSE_HOST

# ---------- 可选：HuggingFace 国内镜像 ----------
HF_ENDPOINT = os.getenv("HF_ENDPOINT", "https://hf-mirror.com")
if HF_ENDPOINT:
    os.environ.setdefault("HF_ENDPOINT", HF_ENDPOINT)

# ---------- MinerU（PDF 版面解析） ----------
MINERU_MODEL_SOURCE = DEFAULT_SETTINGS.MINERU_MODEL_SOURCE
MINERU_BACKEND = DEFAULT_SETTINGS.MINERU_BACKEND
MINERU_METHOD = DEFAULT_SETTINGS.MINERU_METHOD
MINERU_LANG = DEFAULT_SETTINGS.MINERU_LANG
MINERU_TIMEOUT = DEFAULT_SETTINGS.MINERU_TIMEOUT
os.environ.setdefault("MINERU_MODEL_SOURCE", MINERU_MODEL_SOURCE)

# ---------- 检索参数 ----------
HYBRID_FETCH_K = DEFAULT_SETTINGS.HYBRID_FETCH_K
RERANK_TOP_K = DEFAULT_SETTINGS.RERANK_TOP_K
RRF_K = DEFAULT_SETTINGS.RRF_K
