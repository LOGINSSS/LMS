# 跨端 RAG 架构演进计划（Java 管理面 + Python 能力面）

> 状态：**方案积淀中（未实现）** · 日期：2026-08 · 关联代码：`lms-kb/`（Java 现状实现）、`docs/src/rag/`（Python 版 multimodal-rag）
>
> 目标：解决 Java 多模态解析短板（PDF 版面/MinerU、图片 OCR/RapidOCR、本地重排无 Java 等价物），
> 让 Python 端接管知识库重计算能力，Java 保留管理面与平台能力，两条链路分别走 Kafka（入库）与 gRPC（查询）。

---

## 1. 背景与动机

### 1.1 现状：两套实现，各有所长

| 能力 | Java lms-kb（现状） | Python multimodal-rag（docs/src/rag） |
|---|---|---|
| 文档解析 | POI（docx/pptx）、PDFBox 纯文本 | **MinerU 版面解析**（PDF 表格/公式/OCR）、PyMuPDF、PowerPoint COM 渲染兜底 |
| 图片 OCR | Qwen-VL API 替代（每次多一次调用） | **RapidOCR**（本地 onnx，免费） |
| 切片 | Markdown 标题分片 + 递归兜底 | 同思路（langchain splitter） |
| embedding | DashScope text-embedding-v3 API | 同款 API |
| 向量库 | ES 8.13（BM25 + dense_vector + RRF） | **Milvus 2.5**（原生 BM25 函数 + dense + RRF，已跑通） |
| 检索 | ES 混合检索（中文需 ik 插件未装） | bm25s + pkuseg 中文分词 + 稠密 + RRF |
| 精排 | DashScope gte-rerank API | **本地 Qwen3-Reranker-0.6B**（CrossEncoder） |
| 生成 | Qwen-VL（AgentScope） | Qwen-VL / DeepSeek |
| 平台能力 | ✅ Spring 生态：Nacos/网关/鉴权/状态机/RAGAS 采集 | ❌ 无 |
| 多模态完整度 | ⚠️ 差距：PDF 版面、扫描件、纯图页 | ✅ 完整 |

**结论**：平台能力在 Java（现成且完整），多模态重计算能力在 Python。跨端是扬长避短的合理架构，也是当前主流做法（AI 重计算下沉 Python，业务平台留在 Java/Go）。

### 1.2 触发点

- lms-kb 的 PDF 解析退回纯文本、扫描版 PDF 丢失内容、pptx 纯图页无兜底——**"同样效果"存在实际差距**
- Python 版已把整条链路跑通（ingest.py / store.py / graph.py），迁移成本低
- Kafka 基础设施已就绪但零使用，正好作为异步通道的落地场景

### 1.3 版面解析为什么是刚需（不只是"锦上添花"）

教学场景的知识库来源主要是 **PDF 讲义/教材/论文**，这类文档的版面结构（表格、公式、双栏、扫描件）是信息的主体。没有版面解析（Java PDFBox 现状）时：

| 文档特征 | 无版面解析的损失 | 对 RAG 的影响 |
|---|---|---|
| 表格 | 被拆成散乱文本行，行列关系丢失 | 检索命中但语义破碎，答案张冠李戴 |
| 公式 | 乱码/丢失 | 关键内容直接消失，检索不到 |
| 双栏/多栏论文 | 阅读顺序错乱（左栏读一半跳右栏） | 切片内容错位，Faithfulness 下降 |
| 页眉页脚 | 混入正文成为噪声 | 检索噪声多，Context Precision 下降 |
| 扫描件（图片型 PDF） | 整篇丢失（Java 无 OCR 兜底） | Context Recall 直接归零 |

**切片质量决定检索质量的上限**——RAGAS 评估里 `Context Recall` 低的第一个优化方向就是解析与切片（见 RAG评估.md 的优化方向表）。所以版面解析是**决定知识库质量上限的能力**，不是可选项。

**MinerU（上海 AI 实验室开源）能力与接入形态**（对齐构建知识库.md）：

- 版面检测 + 阅读顺序还原、**去页眉页脚**、**公式识别**、**表格还原**、**OCR**（扫描件）
- 输出**结构严谨的 Markdown**（含表格/公式），直接对接现有 Markdown 标题分片链路
- **格式覆盖广**：PDF / HTML / PPT / PPTX / DOC / DOCX / XLS / XLSX / 图片——理论上一个加载器覆盖 90% 企业需求
- **三种接入形态**：
  1. **公共 API**（mineru.net）：Flash 模式免 Token；Precision 模式需 Token（支持 language/ocr 参数）。**每天免费 5000 个 ≤200 页文档，<20 页不限量**——练手/教学场景基本够用，零部署成本
  2. **本地部署**：追求性能需 GPU（视觉模型/OCR/CUDA）；数据隐私要求高时选择
  3. **SDK**：`mineru-open-sdk`（原生，可自由处理 markdown/images/json）、`langchain-mineru`（`MinerULoader` 直接得 Document 集合）
- 落地建议：**阶段 1 直接走公共 API（Flash）打通链路，按需再切本地部署**（见 §7/§8）

---

## 2. 目标架构

```
                外网入口（保持现状，不动）
┌─────────────────────────────────────────────────────────┐
│  nginx ──► Spring Cloud Gateway（lb:// 路由 + JWT 鉴权）  │
└─────────────────────────────────────────────────────────┘
                          │
        ┌─────────────────┼─────────────────┐
        ▼                 ▼                 ▼
   lms-kb(Java)      lms-ai(Java)      lms-media(Java)
   管理面：            对话入口            文件上传/下载
   知识库/文档 CRUD    Feign→lms-kb       （已有，返回 URL）
   状态机/评估采集/RAGAS
        │                 │
        │ ① 入库：Kafka（异步）  ② 查询：gRPC（同步/流式）
        │  topic: lms-kb-doc-ingest   proto: RagService
        ▼                 ▼
┌─────────────────────────────────────────────────────────┐
│            rag-worker（Python 能力面）                    │
│  解析(MinerU/RapidOCR) → 切片 → embedding → Milvus       │
│  → 混合检索(BM25+稠密+RRF) → 本地重排 → Qwen-VL 生成      │
│  （直接复用 docs/src/rag 的 ingest/store/graph 代码）     │
└─────────────────────────────────────────────────────────┘
```

### 2.1 概念澄清（重要）

- **OpenFeign 是 Java 内网服务间调用**（Spring Cloud 微服务内部契约，接口注解编译期绑定），不是外网机制。
- **外网入口是 nginx → Spring Cloud Gateway**（`lb://` 路由 + 白名单）。
- "外网继续 openfeign" 的正确理解：**Java 服务之间（如 lms-ai → lms-kb）保持 Feign 不变，外网入口机制不变**；
  新增的只是 **Java ↔ Python 之间**的通道（Kafka + gRPC），不影响现有调用链。

---

## 3. 通道选型论证

### 3.1 入库链路 → Kafka（强烈支持）

| 维度 | 说明 |
|---|---|
| 异步解耦 | 文档解析是重任务（MinerU 分钟级），不该阻塞上传接口；Kafka 天然异步削峰 |
| 与现有栈一致 | docker-compose 已有 Kafka 3.8.0（KRaft 单节点，本地 29092），spring-kafka 已管版本，**零新增基础设施** |
| 状态机天然适配 | `knowledge_doc.status`（0待解析→…→3完成/4失败）正好由消费端推进 |
| 失败重试 | Kafka 消费重试 + 死信 topic，Python 崩溃不丢任务 |
| 反压 | 批量入库时 Python 消费端限速，不会压垮 Milvus/embedding API |

**为什么不用 gRPC 做入库**：入库不需要同步响应，gRPC 的请求-响应模型反而要维护长连接和回调，复杂度高于消息队列，且消息队列天然支持任务积压/重放。

### 3.2 查询链路 → gRPC（推荐）

| 方案 | 优点 | 缺点 | 结论 |
|---|---|---|---|
| **gRPC** | 跨语言强类型契约（.proto 单一真相源，Java/Python 两端生成代码，杜绝字段对不上）；**原生 server-streaming**（LLM 流式输出/打字机效果）；HTTP/2 | 需引入 proto 工具链；契约演进需版本管理 | ✅ 要流式+强契约时用 |
| HTTP REST（Feign 调 Python） | **最简单，零新增依赖**；Feign 本质是 HTTP 客户端，Python 提供 REST 端点即可被 Feign 调用；延迟与 gRPC 差距可忽略（见下） | 无跨语言强类型契约（Python 按约定实现，字段漂移靠人肉对齐）；**不支持流式响应**（流式要 WebClient/WebFlux 消费 SSE） | ✅ **非流式首推** |
| Thrift | 跨语言 | 生态与工具链不如 gRPC；无 HTTP/2 流式优势 | 不推荐 |

**延迟量级（决定性问题）**：内网单次调用 Feign(HTTP+JSON) 与 gRPC(HTTP/2+protobuf) 差距约 **0.1~1ms**（RTT 相同，序列化差异在亚毫秒级）；而 RAG 问答总延迟 4~10s（大头是 LLM 生成），跨端调用占比 **< 0.3%**——**延迟不是选型理由**。

**真正的决策维度**：
1. **流式输出（打字机效果）**：Feign 不支持流式响应；要流式需 WebClient/WebFlux 消费 Python SSE（轻量）或 gRPC streaming（重型）
2. **跨语言强类型契约**：protobuf 是两端共享单一真相源；REST 靠人肉对齐

**关键结论**：查询是**同步交互链路**（用户等待答案，还要流式），Kafka 不适配（要自己实现 correlation-id + 响应 topic，复杂且延迟高）。**非流式走 Feign→Python FastAPI（零新增依赖），流式再按需引入 gRPC streaming 或 SSE**——gRPC 的理由是流式与强契约，不是延迟。

**过渡路径**：阶段 1 非流式用 Feign 直调 Python FastAPI（契约字段对齐，复用 §5.3 的字段约定）；阶段 3 流式再决定 SSE 或 gRPC。

### 3.3 不动的部分

- 网关、鉴权、前端入口：全部保持现状
- Java 服务间 Feign 调用：保持现状
- lms-media 文件上传：复用（跨端文件传输的载体，见 §5.2）

---

## 4. 职责边界（关键决策点）

| 职责 | Java（lms-kb） | Python（rag-worker） | 说明 |
|---|---|---|---|
| 知识库/文档管理 CRUD | ✅ | — | course_id 唯一绑定、权限 |
| 文档上传 | ✅ | — | 先落 lms-media 拿 URL |
| 文档解析/切片 | — | ✅ | **统一 MinerU**（PDF/docx/pptx/图片，90% 格式覆盖，见 §1.3）+ RapidOCR 兜底 |
| embedding | — | ✅ | DashScope text-embedding-v3 |
| **向量库与检索** | — | ✅（Milvus） | **最大决策点，见下** |
| 精排 | — | ✅ | 本地 Qwen3-Reranker 或 DashScope API |
| 生成 | — | ✅ | Qwen-VL / DeepSeek |
| 状态机推进 | ✅（落库） | ✅（触发推进） | 见 §6 |
| 评估样本采集 + RAGAS 导出 | ✅ | 回传数据 | 复用现有 eval 闭环 |
| 鉴权/网关/Nacos | ✅ | 注册 Nacos | — |

### 4.1 最大决策点：向量库归属

- **方案 A（推荐）：全归 Python（Milvus）**。Python 版 `store.py` 已跑通 Milvus BM25+稠密+RRF，几乎原样可用；Java 侧 `EsKnowledge` 退役。检索与入库同栈，语义一致。
- **方案 B：继续 ES，Python 通过 HTTP 调 Java 检索**。违背"查询走 Python"初衷，且 ES 中文 BM25 还受 ik 插件限制，不推荐。
- 影响面：若选 A，lms-kb 的 `rag/store/EsKnowledge.java`、`ingest/EmbeddingService` 等代码**保留不删**（作为 fallback/离线重建工具），主链路切 Python。

### 4.2 评估闭环归属

- Python 生成答案后，gRPC 响应携带 `answer + sources + contexts`，**Java 侧继续用现有 `EvalSampleCollector` 落库**、`/eval/export` 导出、`scripts/ragas-eval/eval.py` 评估——评估部分完全不动。
- 好处：评估样本口径与线上一致（Java 是唯一写入方，无双写一致性问题）。

---

## 5. 接口契约草案

### 5.1 Kafka：入库通道

```yaml
topic: lms-kb-doc-ingest        # 分区：1（练手）→ 按 courseId hash（量大时）
消息体（JSON）:
  docId:     123456              # knowledge_doc.id
  kbId:      1
  courseId:  1
  fileName:  "xxx.pdf"
  fileType:  "pdf"
  fileUrl:   "http://localhost:8088/uploads/xxx.pdf"   # lms-media 返回的访问 URL
  retryCount: 0                  # 消费端失败重试计数
```

- 生产者：lms-kb `KbServiceImpl.uploadDoc`（落库 status=0 → 发消息 → 返回）
- 消费者：rag-worker（Python，`confluent-kafka` 或 `aiokafka`）
- 处理完成回写：见 §6

### 5.2 文件传输（两条路选一）

1. **走 lms-media（推荐）**：Java 上传后拿 `fileUrl`，Python 按 URL 拉取（HTTP GET）。零共享存储依赖，与现有媒资体系一致，文件生命周期由 lms-media 管理。
2. **共享挂载目录**：本地磁盘/NFS 挂载同一路径，消息只传相对路径。部署简单但耦合存储布局，不适合多机。

### 5.3 查询通道契约（proto 草案；**非流式阶段字段一致，协议走 REST，gRPC 仅流式启用**）

```protobuf
syntax = "proto3";
package rag.v1;

service RagService {
  // 非流式问答
  rpc Chat(ChatRequest) returns (ChatResponse);
  // 流式问答（LLM 打字机效果，阶段 3 启用）
  rpc ChatStream(ChatRequest) returns (stream ChatChunk);
}

message ChatRequest {
  int64 course_id = 1;   // 定位该课程知识库
  string question = 2;
}

message RagSource { string source = 1; string doc_type = 2; string text = 3; }

message ChatResponse {
  string answer = 1;
  repeated RagSource sources = 2;
  repeated string contexts = 3;   // 评估用：RAGAS retrieved_contexts
}

message ChatChunk { string delta = 1; }   // 流式增量文本
```

- Java 侧：`grpc-java` + `protobuf-maven-plugin` 生成 Stub；lms-kb `RagController` 转发 gRPC 给 rag-worker
- Python 侧：`grpcio` + `grpc_tools` 生成，复用 `graph.py` 的 `build_graph` 逻辑
- 契约版本：proto 包名带 `v1`，破坏性变更升 `v2`（新旧共存）

### 5.4 服务注册与发现

- **非流式（Feign 直调）**：Python 服务注册到 Nacos（HTTP 健康检查），Feign `@FeignClient(name="rag-worker")` 走 lb 负载均衡，与调 Java 服务一致
- **gRPC（流式阶段）**：Python 服务另暴露独立 gRPC 端口；gRPC 客户端地址练手阶段直连配置（`grpc://host:port`），后续可用 Nacos 的 gRPC name resolver 或单独负载均衡器（TODO）

---

## 6. 状态同步与一致性

### 6.1 入库状态机

现状 `knowledge_doc.status`：`0待解析 → 1解析中 → 2向量化中 → 3完成 / 4失败`

推进方式（二选一，推荐 A）：

- **A（推荐）：Python 直接写 lms_kb 库**。简单直接，Python 连 MySQL（新增依赖），按 `docId` 更新状态。风险：Python 耦合 Java 库表结构，表结构变更需同步。
- **B：回调 Java 接口**。Python 处理完调 `POST /kb/internal/docs/{docId}/status`（Feign 内部接口，网关白名单/内网隔离）。解耦但多一跳，需保证回调幂等与重试。

### 6.2 幂等与失败补偿

- 消费幂等：按 `docId` 幂等（重复消息不重复入库；Milvus 侧按 doc_id 先删后插）
- 失败重试：消费异常 → `retryCount++` → 重投递，超过阈值进死信 topic `lms-kb-doc-ingest-dlq`
- 状态兜底：`status=4(failed)` + `error_msg` 落库，前端可见可重试（复用现有字段）

---

## 7. 部署与运维

| 项 | 说明 |
|---|---|
| rag-worker 形态 | Python 服务（FastAPI 健康检查 + gRPC server + Kafka 消费线程），可 Docker 化 |
| 依赖 | 解析：MinerU（三形态见 §1.3，**推荐先走公共 API：Flash 免 Token / Precision 需 Token，每天 5000 页免费额度**）、RapidOCR（onnx）；重排：Qwen3-Reranker（模型下载，国内走 hf-mirror）；向量：pymilvus；模型：DashScope SDK |
| 向量库 | **新增 Milvus 2.5 容器**（docker-compose 增加 `milvus-standalone`），Python 版已兼容 |
| 配置 | API Key 与 Java 共用环境变量；Kafka/Nacos 地址走环境变量 |
| 监控 | 入库延迟/成功率、gRPC P99、队列积压（Kafka lag） |

---

## 8. 风险与对策

| 风险 | 影响 | 对策 |
|---|---|---|
| 双栈运维成本（Java+Python 两套部署/监控/依赖） | 人力 | rag-worker 只承载重计算，保持无状态、Docker 化；能力面收敛单一职责 |
| MinerU 本地部署重（PyTorch 显存/模型） | 启动门槛 | **先走公共 API**（免费额度覆盖练手/教学），数据隐私要求高或量大再切本地部署（GPU 可选，CPU 降级慢但可用）；接入层抽象（`MinerUClient` 接口），API/本地可切换 |
| 状态回写一致性 | 文档状态与 Milvus 实际不一致 | 幂等 + 死信 + 状态机兜底（§6）；提供"按 docId 重建"的补偿接口 |
| gRPC 契约演进 | 字段漂移/兼容破坏 | proto 包名版本化（v1/v2），破坏性变更不同步升级；若走 REST 则靠契约测试防漂移 |
| 流式链路延迟 | 用户体验 | Python → SSE / gRPC streaming → Java → 前端 SSE，链路加 Tracing 观测（可选 LangFuse，Python 版已有 observability.py） |
| 评估口径漂移 | 优化决策失真 | 评估采集保留在 Java 单写（§4.2），跨端响应必须带回 contexts/sources |

---

## 9. 渐进路线图（每阶段可验收、可回退）

| 阶段 | 内容 | 验收标准 | 回退策略 |
|---|---|---|---|
| **0（现状基线）** | lms-kb Java 全链路可用 | 现有 RAGAS 评估可跑出基线报告 | — |
| **1（入库跨端）** | Java 上传 → Kafka → Python 解析入库 Milvus → 状态回写；Java 检索/生成保留 | 文档状态 0→3 走通；Milvus 有切片；与 Java 入库结果可对比 | 关 Kafka 开关，回退 Java 管道 |
| **2（查询跨端·非流式）** | lms-kb `RagController` **Feign 转发** rag-worker（Python FastAPI 提供 REST 端点，字段对齐 §5.3）；评估采集沿用 Java | `/rag/chat` 走 Python 链路返回答案+来源；评估样本照常落库 | 配置切换回 Java 实现 |
| **3（查询跨端·流式）** | 流式输出：轻量走 **WebFlux→Python SSE**，重型走 gRPC `ChatStream`，Java 转发 SSE 给前端 | 前端打字机效果；链路 P99 达标 | 同阶段 2 |
| **4（能力增强）** | 本地 rerank/MMR/参数实验；ik 不再需要（Milvus BM25 自带中文分词） | 对比阶段 0 基线，RAGAS 各指标提升 | 分指标开关 |

> 阶段 1~2 完成后，Java 侧 `EsKnowledge` / `ingest` 保留为 fallback 与离线重建工具，不删除。

---

## 10. 可行性结论

1. **方向可行且合理**：混合架构（Java 平台面 + Python 能力面）是跨语言 AI 应用的成熟做法，且 Python 版代码已跑通，迁移成本主要是**通道打通**而非重写算法。
2. **入库走 Kafka：强烈支持**。异步解耦、栈已有、状态机天然适配，代价最小。
3. **查询通道：非流式走 Feign→Python FastAPI（零新增依赖，延迟与 gRPC 差距可忽略，见 §3.2）；gRPC 只在要流式+强契约时引入**——延迟不是选型理由，流式才是。
4. **最大决策点待确认**：向量库归 Python（Milvus，推荐）后，Java 侧 EsKnowledge 退役为 fallback。
5. **最大代价**：双栈运维 + Milvus 部署（MinerU 可先走公共 API 免部署，见 §1.3/§7）；**最大收益**：版面解析补齐（PDF 表格/公式/扫描件/纯图页，见 §1.3），切片质量决定检索上限，评估指标可量化验证。

---

## 待确认清单（下一步讨论）

- [ ] 向量库归属：Milvus（Python 全管）确认？
- [ ] 状态回写方式：Python 直连 MySQL（A）vs 回调 Java（B）？
- [ ] 文件传输：走 lms-media URL（推荐）确认？
- [ ] 阶段 1 先 HTTP 还是直接上 gRPC？
- [ ] rag-worker 命名与仓库归属（monorepo 内 `lms-rag-worker/` 目录 vs 独立仓库）
