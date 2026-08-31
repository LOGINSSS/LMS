# RAGAS 评估（量化 RAG 链路效果）

对齐 `docs/rag文档/RAG评估.md`：用 **RAGAS 0.4.x** 新版 API 对 lms-kb 的 RAG 链路做离线量化评估。

## 评估闭环

```
用户问答 → lms-kb RAG 五步流水线 → 中间产物自动落库 rag_eval_sample
    → 人工标注 reference（/eval/samples/{id}/ground-truth）
    → GET /eval/export 导出（ragas 0.4.x 强制字段）
    → python eval.py 计算 6 指标 → 评估报告
```

## 数据集字段（RAGAS 0.4.x 强制，旧版字段已废弃）

| 新版字段 | 旧版字段 | 含义 | 需要 reference 的指标 |
|---|---|---|---|
| `user_input` | question | 用户问题 | Context Recall / Entities Recall / Noise Sensitivity |
| `retrieved_contexts` | contexts | 检索召回片段列表 | 所有检索指标 |
| `response` | answer | 生成回答 | Faithfulness / Answer Relevancy / Noise Sensitivity |
| `reference` | ground_truth | 标准答案 | Context Recall / Entities Recall / Noise Sensitivity |

## 6 个核心指标与目标阈值（来自 RAG评估.md）

| 指标 | 含义 | 目标 | 低分问题 → 优化方向 |
|---|---|---|---|
| ContextPrecision | 上下文精度（含排名） | >= 0.85 | 检索噪声多 → 优化 chunk、Query Rewriting |
| ContextRecall | 上下文召回 | >= 0.70 | 缺信息 → 调 embedding、增大 k、Hybrid Search |
| ContextEntityRecall | 实体召回 | >= 0.70 | 实体未覆盖 → BM25、优化 chunk |
| Faithfulness | 忠实度（反幻觉） | >= 0.90 | 模型编造 → 加强系统提示、降 temperature |
| AnswerRelevancy | 回答相关性 | >= 0.88 | 跑题 → 优化 Prompt |
| NoiseSensitivity | 噪声敏感度 | <= 0.10（越低越好） | 噪声致错 → Cross-Encoder 重排 |

## 使用步骤

```bash
# 1. 安装（注意版本：ragas 最新 0.4.x，与旧版 API 差异大）
pip install "ragas>=0.4.3" openai

# 2. 设置评判器环境变量（RAGAS 0.4.x 走 OpenAI 协议）
set DEEPSEEK_API_KEY=sk-xxx
set DASHSCOPE_API_KEY=sk-xxx

# 3. 导出评估数据集（courseId 可省略）
curl -s "http://localhost:8096/eval/export?courseId=1" > dataset.json

# 4. 运行评估（DeepSeek 评判 LLM + DashScope text-embedding-v3 向量）
python eval.py --dataset dataset.json --output ragas_report.json
```

## 标注标准答案

```bash
curl -X POST "http://localhost:8096/eval/samples/1/ground-truth" \
  -H "Content-Type: application/json" \
  -d '{"groundTruth": "标准答案文本"}'
```

## 兼容性注意（RAG评估.md 提到的坑）

- RAGAS 0.4.x 在 `ragas/llms/base.py` 硬导入 `langchain_community.chat_models.vertexai`，
  langchain-community 0.4+ 已移除该模块。若报 `ModuleNotFoundError`，在
  `.venv/Lib/site-packages/langchain_community/chat_models/` 下建 `vertexai.py` 空壳：
  ```python
  from langchain_core.language_models import BaseChatModel
  class ChatVertexAI(BaseChatModel):
      pass
  ```
- 评判器默认 OpenAI 协议，需用 OpenAI SDK 连接 DeepSeek/DashScope（脚本已处理）
- 评估耗时较长（每样本 6 指标多次 LLM 调用），建议样本 20~50 条
