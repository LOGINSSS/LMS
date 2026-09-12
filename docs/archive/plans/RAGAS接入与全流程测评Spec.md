# RAGAS 接入与全流程测评 Spec（Python rag 项目）

> 状态：**Spec 定稿（待实现）** · 目标代码库：`docs/src/rag/`（multimodal-rag Python 版）
> 关联：`docs/rag文档/RAG评估.md`（RAGAS 0.4.x 教程）、`docs/跨端RAG架构演进计划.md`（后续跨端衔接）
> 核心依赖：RAGAS 0.4.x 新版 API（强制字段 `user_input/response/retrieved_contexts/reference`）

---

## 0. 目标与范围

给 rag 项目接入 RAGAS 量化评估，打通**从优质文档准备 → 入库 → 测试集设计 → 样本采集 → 评估 → 报告解读 → 优化闭环**的完整链路，让每次改动都有指标可归因、可对比。

**关键前置事实（降低实现成本）**：`rag/graph.py` 的 `graph.invoke()` 返回值已包含完整 RAGState——
`question / rewritten / hyde / queries / retrieved / reranked / answer / sources`。
因此**样本采集零改造**：循环调用 `RAGClient.ask(question)` 即可拿到评估所需的全部字段。

---

## 1. 总体流程

```
① 优质文档准备        ② 入库         ③ 测试集设计         ④ 样本采集
知识库文档标准      ingest_file    resources/ragas_    循环 RAGClient.ask()
(选材/质检)        (现有能力)      eval_dataset.json    + 限流 + 持久化
                                   (question+reference)
        │                            │                     │
        ▼                            ▼                     ▼
⑦ 优化闭环           ⑥ 报告解读       ⑤ 评估执行
改一处→重评→对比   阈值对照+归因    组装 ragas.Dataset
只改一个变量       低分→动作映射    @experiment 6 指标
```

---

## 2. 阶段 A：优质文档准备（测评质量的上限）

> 评估结论的可信度 = 测试集质量。脏数据/坏问题会让所有指标失真且无法归因。

### 2.1 知识库文档标准

| 检查项 | 标准 | 说明 |
|---|---|---|
| 选材 | 覆盖目标领域（如教育学讲义/教材章节） | 与线上使用场景一致 |
| 结构 | 有标题层级、表格、公式 | MinerU 解析后是结构严谨的 Markdown |
| 规模 | 每主题 5~20 页 | 宁缺毋滥 |
| 编码 | UTF-8，无乱码 | 中文文档注意 |
| 标题 | 语义化、层级正确（#/##/###） | 直接决定切片质量 |
| 重复 | 无重复内容 | 重复会虚高检索指标 |
| 质检 | 入库后抽查 chunk（`rag ingest` + 手动看切片） | 切片错乱=文档问题，先修文档再谈评估 |

**注意**：切片质量决定检索质量上限（RAGAS 的 Context Recall 低时第一个查的就是解析/切片）。文档本身脏，后面所有优化都是白费。

### 2.2 测试问题集设计（`resources/ragas_eval_dataset.json`）

问题类型覆盖矩阵（**每类至少 5 条**，评估集总量 30~100 条起步）：

| 类型 | 示例 | 主要压测指标 | 目的 |
|---|---|---|---|
| 事实直答（单跳） | "孔子的教育思想有哪些？" | CP / CR / Faithfulness | 基础检索+生成 |
| 综合归纳（多跳） | "对比孟子和荀子的人性论" | CR / AR | 多片段整合 |
| 术语/名词解释 | "什么是班级授课制？" | CER | 实体召回 |
| 表格数据提取 | "表格中 2023 年招生人数是多少？" | CP | 表格原子块 |
| 数字/年份 | "《学记》成书于什么时期？" | CER | 数字实体 |
| 否定/排除 | "下列哪项不是孔子的主张？" | AR / Faithfulness | 防答非所问 |
| 无答案问题 | "夸美纽斯的生平细节？"（库中没有） | Faithfulness | 应拒绝回答不编造 |
| 口语化/指代 | "那个提出因材施教的人还说了啥？" | 全链路 | 压测 rewrite 环节 |

**JSON 格式**（旧字段名，组装时映射到 ragas 新字段）：
```json
[
  {"question": "孔子的教育思想有哪些？", "ground_truth": "孔子主张有教无类、因材施教、启发诱导……"},
  {"question": "孟子和荀子的人性论有什么不同？", "ground_truth": "孟子主性善论……荀子主性恶论……"}
]
```

### 2.3 ground truth（reference）标注规范

1. **原子陈述化**：每条 reference 拆成可独立判断的陈述句（RAGAS 的 Context Recall 会把 GT 拆解成原子陈述逐一验证），例如"孔子主张有教无类。孔子提出因材施教。"比一段长文更利于精确归因
2. **只含知识库覆盖的信息**：reference 超出知识库范围 → Context Recall 必低且无法通过检索优化，先核对文档
3. **无答案类问题**：ground_truth 留空（评估 Faithfulness 是否诚实拒绝）
4. **一致性**（可选）：两人独立标注，不一致处讨论合并

---

## 3. 阶段 B：RAGAS 接入（依赖 + 评判器）

### 3.1 依赖（pyproject.toml）

```toml
# uv add ragas openai
ragas = ">=0.4.3,<0.5"
openai = ">=1.x"
```

**兼容性坑**（RAG评估.md 已记录）：RAGAS 0.4.x 硬导入 `langchain_community.chat_models.vertexai`，
langchain-community 0.4+ 已移除。报 `ModuleNotFoundError` 时在
`.venv/Lib/site-packages/langchain_community/chat_models/vertexai.py` 建空壳：
```python
from langchain_core.language_models import BaseChatModel
class ChatVertexAI(BaseChatModel):
    pass
```

### 3.2 评判器（Judge）配置

RAGAS 0.4.x 默认 OpenAI 客户端协议，**不能用项目现有的 LangChain ChatDeepSeek**，需独立初始化：

```python
# rag/eval.py 内（或独立 rag/eval_judges.py）
from openai import AsyncOpenAI
from ragas.llms import llm_factory
from ragas.embeddings.base import embedding_factory

llm_client = AsyncOpenAI(
    api_key=os.getenv("DEEPSEEK_API_KEY"),
    base_url=os.getenv("DEEPSEEK_BASE_URL", "https://api.deepseek.com"),
)
embedding_client = AsyncOpenAI(
    api_key=os.getenv("DASHSCOPE_API_KEY"),
    base_url=os.getenv("DASHSCOPE_BASE_URL", "https://dashscope.aliyuncs.com/compatible-mode/v1"),
)
EVALUATOR_LLM = llm_factory(model="deepseek-chat", client=llm_client, max_tokens=384000)
EVALUATOR_EMBEDDINGS = embedding_factory(model="text-embedding-v3", client=embedding_client)
```

- 评判 LLM 用 **deepseek-chat**（快、够强）；不要用 R1（慢且评估不需要推理）
- 与项目 `LLMRuntime` 的职责边界：`LLMRuntime` 服务线上问答，`EVALUATOR_*` 只服务评估，两者不混用

---

## 4. 阶段 C：样本采集（新增 `rag/eval.py` 的核心）

### 4.1 数据来源与口径

| Dataset 字段 | 来源（RAGClient.ask 返回值） | 说明 |
|---|---|---|
| `user_input` | question（测试集） | 循环入参 |
| `response` | result["answer"] | 生成答案 |
| `retrieved_contexts` | **result["reranked"] 的 text 列表** | 最终用于生成的片段（与 Faithfulness 口径一致） |
| `reference` | 测试集 ground_truth | 人工标注 |

> 口径说明：`retrieved`（精排前）与 `reranked`（精排后）都有值。**Dataset 用 reranked**（模型实际看到的）；
> `retrieved` 可额外落盘用于诊断（对比能看出 rerank 是否提升了检索质量）。

### 4.2 采集器实现要点

```python
def build_dataset(testset_path: str, root_dir: str = "./experiments", delay: float = 1.0) -> Dataset:
    """加载测试集 → 循环 ask → 组装 ragas 0.4.x Dataset。已存在则直接 reload（幂等）。"""
    dataset = Dataset(name="rag_eval", backend="local/csv", root_dir=root_dir)
    if len(dataset) > 0:
        print(f"加载已有数据集 {len(dataset)} 条，跳过采集")
        return dataset
    with RAGClient.from_project_config() as client:
        for item in json.load(open(testset_path, encoding="utf-8")):
            result = client.ask(item["question"])
            dataset.append({
                "user_input": item["question"],
                "response": result.get("answer", ""),
                "retrieved_contexts": [d["text"] for d in result.get("reranked", [])],
                "reference": item.get("ground_truth", ""),
            })
            time.sleep(delay)   # DeepSeek 免费版限流，1~2s
    dataset.save()
    return dataset
```

要点：
- **幂等**：`Dataset(name=..., backend="local/csv")` + reload 判断，已有数据不重跑（省 token）
- **限流**：`REQUEST_DELAY=1~2s`（DeepSeek 免费版），付费可调小
- **失败隔离**：单条 ask 异常记日志跳过，不中断整批
- 可复用 `observability.get_langfuse_callback()` 给采集过程加 trace（可选）

---

## 5. 阶段 D：评估执行（`rag/eval.py` 的 run 部分）

```python
from pydantic import BaseModel
from ragas import experiment
from ragas.metrics.collections import (
    ContextPrecision, ContextRecall, ContextEntityRecall,
    Faithfulness, AnswerRelevancy, NoiseSensitivity,
)

class EvalResult(BaseModel):
    context_precision: float
    context_recall: float
    context_entity_recall: float
    faithfulness: float
    answer_relevancy: float
    noise_sensitivity: float

@experiment(experiment_model=EvalResult)
async def run_rag_eval(row):
    user_input = row.get("user_input")
    response = row.get("response")
    retrieved_contexts = row.get("retrieved_contexts") or []
    reference = row.get("reference")
    # 6 指标各 ascore 一次（参数见 RAG评估.md 3.2）
    # ContextPrecision/Recall 需 (user_input, reference, retrieved_contexts)
    # ContextEntityRecall 需 (reference, retrieved_contexts)
    # Faithfulness 需 (user_input, response, retrieved_contexts)
    # AnswerRelevancy 需 (user_input, response) + embeddings
    # NoiseSensitivity 需 (user_input, response, reference, retrieved_contexts)
    return EvalResult(...)

results = await run_rag_eval.arun(dataset, name="rag_baseline")   # 实验记录落盘 experiments/rag_baseline/
```

---

## 6. 阶段 E：报告解读与阈值

### 6.1 目标阈值（来自 RAG评估.md）

| 指标 | 含义 | 目标 | 方向 |
|---|---|---|---|
| ContextPrecision | 上下文精度（含排名） | ≥ 0.85 | 越高越好 |
| ContextRecall | 上下文召回 | ≥ 0.70 | 越高越好 |
| ContextEntityRecall | 实体召回 | ≥ 0.70 | 越高越好 |
| Faithfulness | 忠实度（反幻觉） | ≥ 0.90 | 越高越好 |
| AnswerRelevancy | 回答相关性 | ≥ 0.88 | 越高越好 |
| NoiseSensitivity | 噪声敏感度 | ≤ 0.10 | **越低越好** |

### 6.2 报告结构（输出 `ragas_report.json`）

1. **总览**：6 指标均值 + 阈值对照（✓/✗）
2. **样本明细**：每条样本 6 指标值（定位具体问题题）
3. **题型聚合**：按 §2.2 的问题类型分组求均值（定位薄弱题型，如"表格题 CP 全低"）

### 6.3 低分归因 → 动作映射

| 指标低 | 根因 | 优化动作 | 改哪 |
|---|---|---|---|
| Faithfulness 低 | 模型编造，未基于文档 | 强化系统提示（"只依据资料回答，禁止添加知识，资料不足直说无法回答"）；降低 temperature | graph.py 的 GENERATE_PROMPT |
| Context Precision 低 | 检索噪声多 | 调小 chunk_size / 加 chunk_overlap；强化 rewrite；rerank top_k 调小 | ingest.py 分片参数、graph.py |
| Context Recall 低 | 需要的信息没检索到 | 换/调 embedding 模型；增大 k（fetch_k/rerank_top_k）；确认 hybrid 两路都生效（BM25 中文分词） | store.py / llm.py / settings.py |
| Context Entities Recall 低 | 关键实体未覆盖 | 检查 BM25 中文分词（pkuseg）；优化 chunk 策略（实体不跨块） | ingest.py / store.py |
| Answer Relevancy 低 | 回答跑题 | 限制回答范围（system prompt 约束主题）；问题改写更聚焦 | graph.py |
| Noise Sensitivity 高 | 噪声文档导致答错 | 加/强化 rerank（gte-rerank 或本地 CrossEncoder）；提升检索精度 | llm.py / graph.py |

---

## 7. 阶段 F：优化闭环（迭代 SOP）

```
① 只改一个变量（单变量原则，否则无法归因）
② 若改动影响切片 → 重新入库（delete + ingest）
③ 用同一测试集重跑 eval（不新增样本，保证可比）
④ 对比基线报告，记录每个指标的变化
⑤ 确认无回归（或明确记录降幅原因）后，再动下一个变量
```

**基线管理**：`experiments/` 下按版本归档报告与实验记录——
`rag_baseline/`、`rag_v2_chunk300/`、`rag_v3_prompt/`...（`@experiment` 的 name 即版本名）。

---

## 8. 项目落地清单（具体文件改动）

| 文件 | 动作 | 内容 |
|---|---|---|
| `pyproject.toml` | 改 | 加 `ragas>=0.4.3,<0.5`、`openai` |
| `rag/eval.py` | **新增** | 评判器初始化、build_dataset（采集）、run（评估）、report（汇总/阈值/题型聚合）——本 spec 核心 |
| `rag/cli.py` | 改 | 加 `rag eval` 子命令：`rag eval --dataset resources/ragas_eval_dataset.json --name rag_baseline` |
| `rag/app.py` | 改（可选） | 加 `POST /eval/run`（触发采集+评估）、`GET /eval/report`（最近报告） |
| `resources/ragas_eval_dataset.json` | 新增 | 测试集（question + ground_truth），按 §2.2 覆盖矩阵 |
| `experiments/` | 新增目录 | ragas.Dataset 持久化 + 报告归档（gitignore） |
| `.env` | 改 | 确认 `DEEPSEEK_API_KEY` / `DASHSCOPE_API_KEY`（`DEEPSEEK_BASE_URL` 可选） |
| `README.md` | 改 | 评估使用说明 + 报告解读 |

**CLI 用法示例**：
```bash
uv run rag eval --dataset resources/ragas_eval_dataset.json --name rag_baseline --output experiments/ragas_report.json
```

---

## 9. 验收标准

- [ ] `rag eval` 命令跑通，6 指标出报告（含阈值对照）
- [ ] 测试集 ≥30 条，覆盖 §2.2 全部问题类型
- [ ] 报告含：总览 / 样本明细 / 题型聚合 三层
- [ ] 基线报告归档（experiments/），可反复对比
- [ ] 优化闭环跑通一次：改 chunk 或 prompt → 重入库 → 重评 → 对比出差异
- [ ] 无答案类问题能正确拒绝（Faithfulness 不因编造扣分）

---

## 10. 与 Java lms-kb / 跨端方案的衔接（前瞻）

- **测试集可复用**：`resources/ragas_eval_dataset.json`（question+ground_truth）字段与 Java 侧 `/eval/samples/{id}/ground-truth` 标注语义一致，跨端后同一套测试集可导出到两端，保证**评估口径跨端一致**
- **contexts 口径对齐**：Python 侧 `retrieved_contexts` 取 `reranked` 文本列表，与 Java 侧 `RagasExportSample.retrievedContexts` 一致
- 跨端实现后（见 `docs/跨端RAG架构演进计划.md`），本评估链路可直接服务 Java 平台面的评估闭环，评估报告作为双端效果对比的依据
