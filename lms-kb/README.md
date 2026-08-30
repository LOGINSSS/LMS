# lms-kb 知识库服务

> 用 AgentScope Java + ES 8.13 复刻 `docs/src/rag`（Python LangGraph 版）的多模态 RAG 效果，
> 并接入 RAGAS 评估量化。知识库归属 **owner_type + owner_id**（spec `PERSONAL_AGENT_SPEC.md` §4.4）：
> 课程知识库 `owner_type=1 + course_id`（旧语义保留），个人知识库 `owner_type=2 + user_id`。

## 职责定位

- 分层：**业务层**（知识库能力）
- 依赖：lms-common、ES 8.13.4（Spring Data ES / 原生 client）、AgentScope Java（模型调用）、POI/PDFBox（文档解析）、DashScope（embedding/rerank/VLM）、DeepSeek（R1 改写 / chat HyDE）

## 运行信息

| 项 | 值 |
|---|---|
| 端口 | 8096（本地 application.yml） |
| 库 | lms_kb（knowledge_base / knowledge_doc / rag_eval_sample） |
| ES 索引 | lms_kb_chunk（text BM25 + dense_vector COSINE + course_id 过滤） |
| Nacos 配置 | nacos-config/lms-kb.yaml |
| 网关路由 | `/kb/**` `/rag/**` `/eval/**` |

## 与 Python 版（docs/src/rag）的对应关系

| Python（LangGraph） | Java（lms-kb） |
|---|---|
| graph.py：rewrite→hyde→retrieve→rerank→generate | `rag/RagService`：Service 方法链（AgentScope 无图编排 API，方法即节点） |
| llm.py：DeepSeek R1/chat + Qwen-VL + embedding + rerank | `config/AgentScopeModels`（AgentScope Model）+ `rag/DashScopeClient`（原生 API） |
| store.py：Milvus BM25+稠密 RRF | `rag/store/EsKnowledge`：ES `rank:rrf` 融合（同 RRF 语义） |
| ingest.py：多模态入库 + 分片 | `ingest/*`：DocumentParser / DocxParser / PptxParser / PdfParser / ImageProcessor / MarkdownSplitter / DocPipelineService |
| RAGAS 评估（RAG评估.md） | `eval/*`：样本自动采集 + `/eval/export` + `scripts/ragas-eval/eval.py` |

## 核心接口

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/kb` | 创建知识库（默认课程库 ownerType=1；传 ownerType=2 为个人库） |
| GET | `/kb/course/{courseId}` | 按课程查知识库 |
| POST | `/kb/course/{courseId}/docs` | 上传文档（触发异步管道：解析→分片→向量化→写 ES） |
| DELETE | `/kb/docs/{docId}` | 删除文档（连带删 ES 切片） |
| GET | `/kb/course/{courseId}/docs` | 文档分页（含处理状态） |
| DELETE | `/kb/course/{courseId}` | 删除知识库（级联删切片） |
| GET/POST/DELETE | `/kb/my`、`/kb/my/docs` | **我的个人知识库**（ownerType=2 + 当前登录用户，spec §4.4） |
| GET/POST/DELETE | `/kb/owner/{ownerType}/{ownerId}`、`/kb/owner/{ownerType}/{ownerId}/docs` | 按归属通用操作（1课程 / 2用户） |
| POST | `/rag/chat` | RAG 问答（body 支持 courseId 或 ownerType+ownerId，个人库按用户检索） |
| GET | `/eval/samples` | 评估样本分页 |
| POST | `/eval/samples/{id}/ground-truth` | 标注标准答案 |
| GET | `/eval/export` | 导出 ragas 0.4.x 数据集（user_input/response/retrieved_contexts/reference） |

## 配置要点

- API Key 走环境变量：`DASHSCOPE_API_KEY`（embedding/rerank/VLM/生成）、`DEEPSEEK_API_KEY`（R1 改写/HyDE）
- ES text 分词器默认 `standard`；安装 ik 插件后把 `lms.kb.analyzer` 改为 `ik_smart` 提升中文 BM25 效果
- PDF 为 PDFBox 纯文本提取（Python 版 MinerU 版面解析无 Java 等价物）；扫描版/复杂版面可对接自部署 MinerU HTTP 服务（TODO）

## 启动

```bash
set DASHSCOPE_API_KEY=sk-xxx
set DEEPSEEK_API_KEY=sk-xxx
mvn -s mvn-settings.xml -pl lms-kb -am spring-boot:run
```

## 目录结构

```
com/lms/kb/
├── KbApplication.java
├── config/            # KbProperties / AiProperties / AgentScopeModels / AsyncConfig
├── knowledge/         # 知识库管理：controller / service / PO / mapper / ES 文档
├── ingest/            # 入库管道：解析器 / 分片 / 图片 / embedding / 异步管道
├── rag/               # RAG：RagService（五步流水线）/ EsKnowledge / DashScopeClient
└── eval/              # RAGAS：样本采集 / 评估接口
```
