"""知识库文件注册表：管理原始文件存储 + 文件元数据索引。

- 原始文件存 `{data_dir}/files/{doc_id}{ext}`
- 注册表存 `{data_dir}/files_index.json`（doc_id -> 文件信息），进程内缓存 + 落盘
- 文件名只作展示；文件身份是 doc_id，同名冲突由前端弹窗让用户决定
  （覆盖 / 加后缀另存 / 取消）。

两种用法：
1. 模块级函数（向后兼容）：`files.all_files()` / `files.add(...)` ——
   默认注册表绑定 config.DATA_DIR（与 FastAPI 服务共用同一份数据）。
2. SDK 实例：`FileRegistry(data_dir)`，每个 RAGClient 一份，数据目录独立。
"""
from __future__ import annotations

import json
import threading
import time
import uuid
from pathlib import Path
from typing import Dict, List, Optional

from . import config


class FileRegistry:
    """按 data_dir 隔离的文件注册表（线程安全）。"""

    def __init__(self, data_dir: Path):
        self.files_dir = Path(data_dir) / "files"
        self.index_path = Path(data_dir) / "files_index.json"
        self._lock = threading.Lock()
        self.files_dir.mkdir(parents=True, exist_ok=True)
        self._index: Dict[str, dict] = self._load()

    def _load(self) -> Dict[str, dict]:
        if self.index_path.exists():
            try:
                return json.loads(self.index_path.read_text(encoding="utf-8"))
            except Exception:  # noqa: BLE001 —— 索引损坏时从空开始
                return {}
        return {}

    def _save(self) -> None:
        self.index_path.write_text(
            json.dumps(self._index, ensure_ascii=False, indent=2), encoding="utf-8"
        )

    @staticmethod
    def new_doc_id() -> str:
        return uuid.uuid4().hex[:12]

    def final_path(self, doc_id: str, filename: str) -> Path:
        """最终存储路径（正式文件名）。"""
        return self.files_dir / f"{doc_id}{Path(filename).suffix.lower()}"

    def staging_path(self, doc_id: str, filename: str) -> Path:
        """暂存路径（等待决策/待入库）。"""
        return self.files_dir / f".staging_{doc_id}{Path(filename).suffix.lower()}"

    def all_files(self) -> List[dict]:
        with self._lock:
            return sorted(
                self._index.values(), key=lambda f: f.get("uploaded_at", 0), reverse=True
            )

    def get(self, doc_id: str) -> Optional[dict]:
        with self._lock:
            return self._index.get(doc_id)

    def find_by_filename(self, filename: str) -> Optional[dict]:
        with self._lock:
            for f in self._index.values():
                if f.get("filename") == filename:
                    return f
        return None

    def add(self, entry: dict) -> None:
        with self._lock:
            self._index[entry["doc_id"]] = entry
            self._save()

    def update(self, doc_id: str, **fields) -> None:
        with self._lock:
            entry = self._index.get(doc_id)
            if entry:
                entry.update(fields)
                self._save()

    def pop(self, doc_id: str) -> Optional[dict]:
        with self._lock:
            entry = self._index.pop(doc_id, None)
            if entry:
                self._save()
            return entry

    @staticmethod
    def make_entry(doc_id: str, filename: str, doc_type: str, status: str = "ingesting") -> dict:
        return {
            "doc_id": doc_id,
            "filename": filename,
            "doc_type": doc_type,
            "chunk_count": 0,
            "status": status,
            "uploaded_at": time.time(),
        }

    def next_rename(self, filename: str) -> str:
        """为同名文件找下一个可用序号名：'x.pdf' -> 'x (1).pdf' -> 'x (2).pdf'…"""
        stem = Path(filename).stem
        ext = Path(filename).suffix
        with self._lock:
            names = {f.get("filename") for f in self._index.values()}
        n = 1
        while f"{stem} ({n}){ext}" in names:
            n += 1
        return f"{stem} ({n}){ext}"


# ---------- 模块级函数（向后兼容）：委托给默认实例 ----------

FILES_DIR = config.DATA_DIR / "files"
_INDEX_PATH = config.DATA_DIR / "files_index.json"

_DEFAULT_REGISTRY: Optional[FileRegistry] = None


def _default_registry() -> FileRegistry:
    global _DEFAULT_REGISTRY
    if _DEFAULT_REGISTRY is None:
        _DEFAULT_REGISTRY = FileRegistry(config.DATA_DIR)
    return _DEFAULT_REGISTRY


def new_doc_id() -> str:
    return _default_registry().new_doc_id()


def final_path(doc_id: str, filename: str) -> Path:
    return _default_registry().final_path(doc_id, filename)


def staging_path(doc_id: str, filename: str) -> Path:
    return _default_registry().staging_path(doc_id, filename)


def all_files() -> List[dict]:
    return _default_registry().all_files()


def get(doc_id: str) -> Optional[dict]:
    return _default_registry().get(doc_id)


def find_by_filename(filename: str) -> Optional[dict]:
    return _default_registry().find_by_filename(filename)


def add(entry: dict) -> None:
    _default_registry().add(entry)


def update(doc_id: str, **fields) -> None:
    _default_registry().update(doc_id, **fields)


def pop(doc_id: str) -> Optional[dict]:
    return _default_registry().pop(doc_id)


def make_entry(doc_id: str, filename: str, doc_type: str, status: str = "ingesting") -> dict:
    return _default_registry().make_entry(doc_id, filename, doc_type, status=status)


def next_rename(filename: str) -> str:
    return _default_registry().next_rename(filename)
