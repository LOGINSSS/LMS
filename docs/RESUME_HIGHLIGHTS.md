# LMS 简历沉淀：一段简历 + 五个关键点

> 依据：项目现状（已实现代码）+ 设计文档（`BUSINESS_MODULES.md` / `PERSONAL_AGENT_SPEC.md` / `跨端RAG架构演进计划.md` / `RAGAS接入与全流程测评Spec.md`，均为后续实现蓝图）。
> 标注：✅ 已实现 · 📐 设计蓝图（后续实现）。写简历时可把 📐 表述为「设计并规划」，避免与已落地能力混淆。

---

## 一段简历（可直接使用）

> **课程学习平台 LMS（微服务 + AI）**：从零搭建 Spring Cloud Alibaba 微服务工程，13 个后端模块 + Vue3 前端，覆盖「认证→课程→媒资→评价互动→搜索→题库→学习→数据看板→AI 对话」完整业务链路；每业务域独立库、独立 Nacos 配置、网关统一 JWT 鉴权路由。基于 AgentScope Java 构建多智能体体系：每人一个个人 Agent（老师/学生），17 个声明式子 Agent 注册表，业务模块 REST 经 Feign 封装为工具，支持邀请制编排（SubAgentTool/EventBus）、两层长期记忆（L1 会话 Redis + L2 ReMe 画像）与 IM 管道/定时任务，并落地固定管道 + 节点 Agent 增强的两条确定性 AI 业务管道：学习评测（学情诊断→课程规划→习题推送→效果测评）与课程生成（课程大纲→章节 MD→中间测试题，MD 与考题 JSON 落课程内容详情库 + 前端 wiki 渲染）。集成既有 Python 多模态 RAG 能力（文档解析/切片/混合检索/生成），通过 OpenFeign 打通 Java 平台面与 Python 能力面的跨语言服务调用，完成知识库管理、文档入库状态机与 RAG 问答闭环，并设计基于 Redis 原子预扣 + Kafka 异步削峰的抢课高并发方案。

---

## 模板风格版本（对齐目标岗位模板）

**技术栈**：Java 17、Spring Boot 3.3.5、Spring Cloud Alibaba（Nacos）、MyBatis-Plus、Kafka、Redis、Elasticsearch 8.13、MySQL 8.0、Spring Cloud Gateway、AgentScope Java（通义千问 DashScope）、Vue3 + Vite、Docker

**项目简介**：AI 驱动的课程学习平台，基于 Spring Cloud Alibaba 微服务架构覆盖「认证→课程→媒资→评价互动→搜索→题库→学习→数据看板→AI 对话」完整业务链路，提供个人智能体、多模态 RAG 知识库、量化评估与 IM 协作等能力，支持多端协同学习。

- **设计并实现多智能体个人 Agent 体系**，基于 AgentScope Java 构建一人一 Agent 运行时与 17 个声明式子 Agent 注册表，通过 Feign 将业务模块 REST 自动封装为可调用工具，结合 SubAgentTool 邀请制编排与事件总线、L1 会话（Redis）+ L2 画像（ReMe）两层长期记忆及 IM 管道与定时任务，打通「学生提问→老师回复」跨角色协作链路。
- **设计并实现跨语言 RAG 能力集成**，复用既有 Python 多模态 RAG 链路（文档解析、切片、混合检索与生成），通过 OpenFeign 打通 Java 平台面与 Python 能力面的跨语言服务调用，完成知识库管理、文档入库状态机与 RAG 问答的服务化封装，Python 侧注册 Nacos 经 Feign 负载均衡调用，配置与密钥统一管理。
- **设计并实现两条确定性 AI 管道**，以固定管道 + 节点 Agent 增强编排业务流（流程顺序由代码确定、LLM 仅做节点内增强，区别于父 Agent 自由邀请编排）：学习评测管道（学情诊断→课程规划→习题推送→效果测评，语义批改 + 确定性兜底，结果写回学习数据中心）与课程生成管道（课程大纲→章节 MD→中间测试题，大纲仅作 MD 生成的中间输入，MD 与考题以 JSON 落课程内容详情库，前端 wiki 渲染），保证 AI 教学业务稳定可复现。
- **设计并实现微服务架构治理体系**，从零搭建 13 模块 Spring Cloud Alibaba 聚合工程，落地每业务域独立库 lms_<domain> 与独立 Nacos 配置，经网关统一 JWT 鉴权路由与 user-info 透传，沉淀 lms-common 公共底座（统一响应/异常/分页/用户上下文/自动配置），并基于 docker-compose 与一键脚本完成基础设施编排与 10+ 服务启停。
- **设计并规划抢课高并发方案**，面向限时限量选课构建异步削峰链路：库存预热 Redis 并以 Lua 原子脚本预扣（防超卖、防一人多抢），扣减成功的请求经 Kafka 异步落库选课记录，配合唯一键幂等、状态机与死信补偿，实现高并发抢课对数据库的削峰保护与最终一致。

---

## 五个关键点

### ① 完整微服务架构治理与全链路业务闭环 ✅

- 从零搭建 Spring Cloud Alibaba（Boot 3.3.5 / Cloud 2023.0.3 / Nacos / MyBatis-Plus / Redisson / Kafka / ES 8.13）13 模块聚合工程，父 pom 统一版本源，子模块零版本号。
- 每业务域**独立库 `lms_<domain>`**、独立 Nacos 配置（13 份 yaml）、网关统一入口：路由转发（lb://）+ JWT 统一鉴权 + 白名单放行 + user-info 头透传 + CORS。
- `lms-common` 公共底座：统一响应/异常体系/分页/工具类/JWT/UserContext，MyBatis-Plus·Swagger·MVC 自动配置（AutoConfiguration.imports），业务模块只写业务。
- 一体化编排与运维：docker-compose 一键拉起 Nacos/MySQL/Redis/Kafka/ES，`push-nacos-config / start-all / stop-all` 脚本一键导入配置、启停全部服务。

**简历话术**：主导 13 模块微服务工程搭建，设计公共底座与统一鉴权网关，实现每业务域独立库 + 配置中心 + 一键编排交付，保障 10+ 服务可控启动与运行。

### ② 基于 AgentScope Java 的多智能体个人 Agent 体系 ✅（阶段 5 加固为 📐）

- **一人一 Agent**：teacher-agent / student-agent 按用户工厂实例化（prompt 注入用户名、绑定可用子 Agent）；17 个 YAML 声明式子 Agent 注册表（course/exam/learning/search/remark/user/media/statistics/kb/im/task + 评测管道 4 节点）。
- **工具面自动封装**：业务模块 REST → Feign Client → `ReflectiveFunctionTool`，@Tool 方法自动生成参数 JSON Schema，一个子 Agent 一组 ToolGroup。
- **邀请制编排**：`SubAgentTool` 父调子（agent-as-tool）+ `SubagentEventBus` 事件广播 + 任务上下文（taskId/超时/工具白名单），互调深度 ≤3、高副作用操作前置人工确认。
- **两层长期记忆**：L1 会话（Redis 多轮消息 + 上下文裁剪）；L2 画像（AgentScope ReMe 提取-遗忘-整合-检索注入，一人一记忆空间，未部署自动回退 MySQL 画像 + LLM 摘要压缩）。
- **学习评测管道**：固定管道 + 节点 Agent（诊断→规划→习题→测评），确定性流程不交 LLM 决策顺序，中间产物落 `agent_task`。
- **IM 管道 + 定时任务**：`ImChannel` 抽象（Console 兜底 / 企微 webhook+回调验签），`agent_task` 落库 + @Scheduled 轮询（可切 Kafka 事件），全部阶段开关可回退。
- 📐 设计蓝图：L3 知识文档记忆、A2A 跨服务互调、画像周期压缩与防滥用加固。

**简历话术**：基于 AgentScope Java 实现多智能体编排体系，声明式注册 + 工具自动封装 + 邀请制互调 + 两层长期记忆，支撑「学生提问→老师 IM 回复」等全链路场景。

### ③ 跨语言 RAG 能力集成（复用 Python RAG + OpenFeign）✅（已决策落地）

- **决策背景**：Java 多模态解析有短板（PDF 版面/扫描件 OCR/本地重排无 Java 等价物），复用既有 Python 多模态 RAG 链路（MinerU/RapidOCR/Milvus 已跑通）——平台能力留 Java、重计算下沉 Python，扬长避短。
- **通道选型（量化论证）**：查询链路非流式走 **Feign → Python FastAPI**（零新增依赖；内网 Feign 与 gRPC 延迟差仅 0.1~1ms，RAG 问答总延迟 4~10s，跨端占比 <0.3%，延迟不是选型理由）；入库可选 Kafka 异步解耦；流式再按需引 SSE/gRPC。
- **Java 管理面**：知识库 CRUD / 文档上传 / 入库状态机（0→3/4）/ 网关鉴权；Feign 封装 rag-worker 客户端，契约字段对齐。
- **Python 能力面**：注册 Nacos（HTTP 健康检查），Feign `lb://` 负载均衡，与调 Java 服务一致；API Key 环境变量共用。

**简历话术**：集成 Python 多模态 RAG 能力面，以 OpenFeign 打通 Java↔Python 跨语言调用，复用既有能力、不重复造轮子，并统一收敛在 Nacos 注册发现与配置管理体系下。

### ④ 两条确定性 AI 管道（固定管道 + 节点 Agent 增强）✅ 学习评测 / 📐 课程生成

- **设计哲学**：流程顺序与数据依赖由代码确定，LLM 只在节点内做增强——确定性业务不交给 LLM 决策调用顺序（区别于对话场景父 Agent 自由邀请的语义编排），可复现、可评估、可回退。
- **管道一 学习评测（✅ 已实现）**：学情诊断 → 课程规划 → 习题推送 → 效果测评四节点，节点内为带工具的 ReActAgent（读学习数据中心 / 检索题库 / 检索课程知识库）；判分由 assess-agent LLM 语义批改 + `QuestionChecker` 客观题确定性兜底，判分明细 Java 直写学习数据中心（做题/错题/测评），中间产物落 `agent_task`。
- **管道二 课程生成（📐 设计蓝图）**：课程大纲 → 按章节生成 MD 文档 → 生成中间测试题；大纲仅作为 MD 生成的中间输入；MD 内容与考题内容以 JSON 落新增「课程内容详情库」（MySQL），前端以 wiki 渲染 MD。
- **复用底座**：两条管道共用同一套节点 Agent + 工具面 + `agent_task` 留存机制。

**简历话术**：以固定管道 + 节点 Agent 增强编排两条确定性 AI 管道（学习评测、课程生成），流程顺序代码管控、LLM 只做节点内增强，兼顾智能与可控。

### ⑤ 抢课高并发方案（Redis 预扣 + Kafka 削峰）📐（设计规划，基础能力 ✅）

- **场景与现状**：限时限量选课（热门课程秒杀式抢课）；现有 `CourseServiceImpl.enroll()` 已实现单事务选课 + 唯一键幂等与并发兜底（✅ 基础能力），高并发加固为设计规划（📐）。
- **库存预热与原子扣减**：课程库存预热到 Redis（`course:stock:{id}`），抢课请求走 Lua 原子脚本「库存 > 0 且 未选过 → 扣减」，杜绝超卖与一人多抢；辅以 Redisson 分布式锁保护库存变更。
- **Kafka 异步削峰**：扣减成功的请求发 Kafka（topic `lms-course-enroll`），消费端异步写 `course_enrollment`（状态机：处理中 → 成功/失败），失败重试 + 死信 topic 补偿——数据库只承担落库峰值，不直接扛抢课洪峰。
- **幂等与一致性**：`uk_course_student` 唯一键 + 消费端按 (studentId, courseId) 去重；前端轮询查询结果，最终一致。
- **基础设施复用**：Kafka 3.8（docker-compose 已就绪）首次作为高并发落地场景；网关鉴权 + 接口限流前置。

**简历话术**：设计抢课高并发链路，Redis Lua 原子预扣防超卖 + Kafka 异步削峰落库，支撑限时限量选课不击穿数据库。

---

## 附录：设计驱动方法（原第五点，保留作面试素材）

- **四份设计文档支撑落地**：六大业务模块蓝图（实现顺序按依赖分批）、个人 Agent Spec（分期路线图：0 基线→5 加固，每阶段可验收可回退）、跨端 RAG 演进计划（可行性结论 + 最大决策点 + 风险对策表）、RAGAS 测评 Spec（验收标准清单）。
- **通道/存储选型论证**：入库走 Kafka vs gRPC、查询走 Feign vs gRPC vs SSE（含延迟量级量化）、向量库归属 Milvus vs ES，均给出决策维度与过渡路径，拒绝「为了技术而技术」。
- **一致性治理**：状态机（入库 0→3/4、任务 0→4）+ 幂等（docId/taskId 去重）+ 死信/失败补偿 + 阶段开关回退，降低改动风险。
- **接口契约**：前端 JSON Schema 与 Java DTO 一一对应；代码注释规范统一；环境变量注入 API Key（无硬编码密钥）。

> 若岗位看重架构方法论而非业务功能，可把本附录内容重新提升为第五点，与抢课点二选一。

---

## 使用建议

1. 简历段落建议控制在 150~220 字中文，按目标岗位裁剪：后端岗突出 ①⑤，AI 岗突出 ②③④。
2. 📐 蓝图项建议表述为「设计并规划了……（计划落地）」或面试时主动说明分期路线，避免被追问已实现细节时穿帮。
3. 面试高频追问点预埋：抢课防超卖（Lua 原子扣减 vs 分布式锁）、Kafka 削峰与消费幂等/死信、固定管道 vs 自由编排的取舍、ReMe 记忆机制、邀请制与 MCP 工具差异、Feign 跨语言调用的契约对齐与超时降级。
