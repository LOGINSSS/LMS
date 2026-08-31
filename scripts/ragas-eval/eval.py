"""
RAGAS 0.4.x 离线评估脚本（对齐 docs/rag文档/RAG评估.md）

用法：
    1. 导出 lms-kb 采集的评估数据集（user_input/response/retrieved_contexts/reference）：
       curl -s "http://localhost:8096/eval/export?courseId=1" > dataset.json
       （courseId 可省略，导出全部课程样本）
    2. 运行本脚本：
       python eval.py --dataset dataset.json --output ragas_report.json

评判器（Judge）：
    - LLM：DeepSeek deepseek-chat（RAGAS 0.4.x 默认 OpenAI 协议，需用 OpenAI SDK 连接 DeepSeek）
    - Embedding：DashScope text-embedding-v3（阿里云百炼，OpenAI 兼容端点）
    环境变量：DEEPSEEK_API_KEY / DEEPSEEK_BASE_URL / DASHSCOPE_API_KEY / DASHSCOPE_BASE_URL

6 个核心指标（ragas.metrics.collections）与目标阈值：
    - ContextPrecision      上下文精度    >= 0.85   （需 reference）
    - ContextRecall         上下文召回    >= 0.70   （需 reference）
    - ContextEntityRecall   实体召回      >= 0.70   （需 reference）
    - Faithfulness          忠实度       >= 0.90
    - AnswerRelevancy       回答相关性    >= 0.88
    - NoiseSensitivity      噪声敏感度    <= 0.10   （越低越好，需 reference）

依赖：pip install "ragas>=0.4.3" openai
"""
from __future__ import annotations

import argparse
import asyncio
import json
import os

from openai import AsyncOpenAI
from pydantic import BaseModel

from ragas import Dataset, experiment
from ragas.embeddings.base import embedding_factory
from ragas.llms import llm_factory
from ragas.metrics.collections import (
    AnswerRelevancy,
    ContextEntityRecall,
    ContextPrecision,
    ContextRecall,
    Faithfulness,
    NoiseSensitivity,
)

# 目标阈值（来自 RAG评估.md，评估报告会附带）
TARGETS = {
    "context_precision": 0.85,
    "context_recall": 0.70,
    "context_entity_recall": 0.70,
    "faithfulness": 0.90,
    "answer_relevancy": 0.88,
    "noise_sensitivity": 0.10,  # 越低越好
}


class EvalResult(BaseModel):
    context_precision: float
    context_recall: float
    context_entity_recall: float
    faithfulness: float
    answer_relevancy: float
    noise_sensitivity: float


def build_judges():
    """初始化评判器（对应 RAG评估.md 3.1：RAGAS 走 OpenAI 协议，用 OpenAI SDK 连 DeepSeek/DashScope）"""
    llm_client = AsyncOpenAI(
        api_key=os.getenv("DEEPSEEK_API_KEY"),
        base_url=os.getenv("DEEPSEEK_BASE_URL", "https://api.deepseek.com"),
    )
    embedding_client = AsyncOpenAI(
        api_key=os.getenv("DASHSCOPE_API_KEY"),
        base_url=os.getenv("DASHSCOPE_BASE_URL", "https://dashscope.aliyuncs.com/compatible-mode/v1"),
    )
    evaluator_llm = llm_factory(model="deepseek-chat", client=llm_client, max_tokens=384000)
    evaluator_embeddings = embedding_factory(model="text-embedding-v3", client=embedding_client)
    return evaluator_llm, evaluator_embeddings


def load_dataset(path: str, root_dir: str) -> Dataset:
    """加载导出的 JSON 为 ragas 0.4.x Dataset（强制字段：user_input/response/retrieved_contexts/reference）"""
    with open(path, encoding="utf-8") as f:
        rows = json.load(f)
    dataset = Dataset(name="rag_eval", backend="local/csv", root_dir=root_dir)
    for row in rows:
        dataset.append({
            "user_input": row["user_input"],
            "response": row.get("response") or "",
            "retrieved_contexts": row.get("retrieved_contexts") or [],
            "reference": row.get("reference") or "",
        })
    return dataset


@experiment(experiment_model=EvalResult)
async def run_rag_eval(row):
    """实验：对单行样本算 6 个核心指标（对应 RAG评估.md 3.3/3.4）"""
    user_input = row.get("user_input")
    response = row.get("response")
    retrieved_contexts = row.get("retrieved_contexts") or []
    reference = row.get("reference")

    cp = ContextPrecision(llm=EVALUATOR_LLM)
    cp_result = await cp.ascore(user_input=user_input, reference=reference, retrieved_contexts=retrieved_contexts)

    cr = ContextRecall(llm=EVALUATOR_LLM)
    cr_result = await cr.ascore(user_input=user_input, reference=reference, retrieved_contexts=retrieved_contexts)

    cer = ContextEntityRecall(llm=EVALUATOR_LLM)
    cer_result = await cer.ascore(reference=reference, retrieved_contexts=retrieved_contexts)

    faith = Faithfulness(llm=EVALUATOR_LLM)
    faith_result = await faith.ascore(user_input=user_input, response=response, retrieved_contexts=retrieved_contexts)

    ar = AnswerRelevancy(llm=EVALUATOR_LLM, embeddings=EVALUATOR_EMBEDDINGS)
    ar_result = await ar.ascore(user_input=user_input, response=response)

    ns = NoiseSensitivity(llm=EVALUATOR_LLM)
    ns_result = await ns.ascore(
        user_input=user_input, response=response, reference=reference, retrieved_contexts=retrieved_contexts
    )

    return EvalResult(
        context_precision=cp_result.value,
        context_recall=cr_result.value,
        context_entity_recall=cer_result.value,
        faithfulness=faith_result.value,
        answer_relevancy=ar_result.value,
        noise_sensitivity=ns_result.value,
    )


async def main() -> None:
    parser = argparse.ArgumentParser(description="RAGAS 0.4.x 评估 lms-kb RAG 链路")
    parser.add_argument("--dataset", required=True, help="lms-kb /eval/export 导出的 JSON 数据集文件")
    parser.add_argument("--output", default="ragas_report.json", help="评估报告输出路径")
    parser.add_argument("--root-dir", default="./experiments", help="ragas.Dataset 本地存储目录")
    args = parser.parse_args()

    dataset = load_dataset(args.dataset, args.root_dir)
    if len(dataset) == 0:
        print("数据集为空：请先在 lms-kb 发起 RAG 问答积累样本，并标注 reference（ground_truth）")
        return

    print(f"开始评估：共 {len(dataset)} 个样本，6 个指标（DeepSeek LLM + DashScope Embedding 评判器）...")
    results = await run_rag_eval.arun(dataset, name="rag_eval")

    # 汇总
    summary = {}
    for field in EvalResult.model_fields:
        summary[field] = [getattr(r, field) for r in results]
    print("=" * 60)
    print("RAGAS 评估报告（对照目标阈值）")
    print("=" * 60)
    report = {"samples": [], "summary": {}}
    for field, target in TARGETS.items():
        values = summary[field]
        avg = sum(values) / len(values)
        better = "越低越好" if field == "noise_sensitivity" else "越高越好"
        ok = avg <= target if field == "noise_sensitivity" else avg >= target
        report["summary"][field] = {"avg": round(avg, 4), "target": target, "better": better, "pass": ok}
        print(f"  {field:24s} avg={avg:.4f}  target {better} {target}  {'✓' if ok else '✗'}")
    report["samples"] = [r.model_dump() for r in results]
    with open(args.output, "w", encoding="utf-8") as f:
        json.dump(report, f, ensure_ascii=False, indent=2)
    print(f"\n评估报告已写入: {args.output}")
    print(f"实验记录已保存至: {args.root_dir}/rag_eval/")


if __name__ == "__main__":
    EVALUATOR_LLM, EVALUATOR_EMBEDDINGS = build_judges()
    asyncio.run(main())
