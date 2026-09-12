# lms-ai AI 能力服务

> AgentScope Java + 阿里云百炼 DashScope（OpenAI 兼容模式）：**单轮 chat（阶段 0）+ 个人 Agent 体系**（个人 Agent / 子 Agent / Session 上下文 / 画像与 ReMe 个人 Wiki / 邀请制编排 / IM 管道 / 定时任务）。

## 职责定位

- 分层：**业务层**（AI 能力 / Agent 运行时）
- 依赖：lms-common（Feign/工具/ORM/Redis/Kafka）、agentscope-core、agentscope-openai-spring-boot-starter、agentscope-extensions-model-openai；ReMe 通过当前 HTTP Job API 对接
- 配置：本地 `application.yml` 兜底，Nacos `lms-ai.yaml` 为唯一配置源（`scripts/push-nacos-config.ps1` 推送）

## 运行信息

| 项 | 值 |
|---|---|
| 端口 | 8095 |
| 库 | `lms_ai`（MySQL 13306；DDL：`docker/mysql/init/11-lms-ai.sql`） |
| Redis | L1 会话消息 `agent:session:{id}:messages`（TTL 1~7 天） |
| Nacos 配置 | `lms-ai.yaml`（新增） |
| 网关路由 | `/ai/**`（单轮 chat）、`/agent/**`（个人 Agent，spec §8.1） |

## 学习评测管道（评测业务线：诊断→规划→习题→测评）

> 设计：**固定管道 + 节点 Agent 增强**——节点顺序与数据依赖由代码确定（`orchestration/LearningPipelineService`），
> 节点内部是带工具的 ReActAgent（读学习数据中心/检索题库/写回错题）。区别于对话场景的"父 agent 自由邀请"（spec §5），
> 确定性流程不交给 LLM 决策调用顺序（需求文档见《Agent智能教育与个性化学习系统》）。

```
学生端 → ①diagnose-agent 学情诊断 → ②plan-agent 课程规划 → ③exercise-agent 习题推送 → ④assess-agent 效果测评
   ↑                                                                            ↓
   └────────────── lms-learning 学习数据中心（做题/错题/测评/学情聚合）←───────────┘
```

| 节点 | Agent 声明 | 工具（lms-ai） | 数据（lms-learning） |
|---|---|---|---|
| ① 诊断 | `diagnose-agent.yaml` | `eval.getDiagnosis` / `eval.myExercises` / `eval.myAssessments` | `GET /learn/stats/diagnosis` 学情聚合 |
| ② 规划 | `plan-agent.yaml` | `kb.ragChatCourse`（课程知识库） | — |
| ③ 习题 | `exercise-agent.yaml` | `exam.queryQuestionPage`（题库检索） | `GET /learn/exercises/mine` |
| ④ 测评 | `assess-agent.yaml` | `exam.getQuestion` + `eval.submitExercise` / `eval.submitAssessment` | `POST /learn/exercises`、`POST /learn/assessments`（回流） |

- 入口：`/agent/learning/diagnose|plan|exercise|assess|evaluate`（全链一次）
- 中间产物（诊断报告/学习路径/题目清单/评估报告）留存 `agent_task.result`（§6.4 可观测性）
- 学习数据中心扩展在 lms-learning：`exercise_record`（做题/错题集）、`learning_assessment`（测评报告）

## 核心接口（spec §8）

| 方法 | 路径 | 说明 | 鉴权 |
|---|---|---|---|
| POST | `/ai/chat` | LLM 单轮对话（阶段 0 基线） | 登录 |
| POST | `/ai/chat/rag` | 课程知识库 RAG 问答（转发 lms-kb） | 登录 |
| POST | `/agent/chat` | 个人 agent 多轮对话 `{sessionId?, text, agentType: student\|teacher}` | 登录 |
| POST | `/agent/sessions` | 建会话 | 登录 |
| GET | `/agent/sessions/mine` | 我的会话列表 | 登录 |
| DELETE | `/agent/sessions/{id}` | 结束会话并异步触发 ReMe Auto Memory | 登录 |
| GET/POST | `/agent/profile/mine` | 我的画像（L2，「记住我」） | 登录 |
| POST | `/agent/memory/wiki` | 显式写入我的 ReMe 个人 Wiki | 登录 |
| PATCH/DELETE | `/agent/memory/wiki/{memoryId}` | 更正原文 / 删除我的 Wiki 记忆 | 登录 |
| POST | `/agent/ask-teacher` | 学生问题 → student-agent → teacher-agent（IM/定时任务编排） | 登录 |
| POST | `/agent/teacher/generate-exam` | 老师调度 exam-agent 出大纲/题目 | 登录 |
| POST | `/agent/teacher/generate-course` | 老师调度 course-agent 建课+大纲+章节 HTML | 登录 |
| POST | `/agent/im/callback/{platform}` | IM 平台回调（验签回流，网关白名单放行） | 验签 |
| POST | `/agent/tasks`、GET `/agent/tasks/{taskId}`、POST `/agent/tasks/{taskId}/cancel` | 定时任务 | 登录 |
| POST | `/agent/behaviors` | 行为事件上报（L2 画像原料） | 登录 |
| POST | `/agent/learning/diagnose` | 节点① 学情诊断 → 学情报告 | 登录 |
| POST | `/agent/learning/plan` | 节点② 课程规划 → 学习路径 | 登录 |
| POST | `/agent/learning/exercise` | 节点③ 习题推送 → 题目清单 | 登录 |
| POST | `/agent/learning/assess` | 节点④ 效果测评 → 评估报告 + 写回数据中心 | 登录 |
| POST | `/agent/learning/evaluate` | 全链一次（诊断→规划→习题） | 登录 |

## Agent 声明（spec §3）

`src/main/resources/agents/*.yaml`，启动时扫描注册（`AgentRegistry`）：

| 声明 | 角色 | 工具面 |
|---|---|---|
| teacher-agent / student-agent | personal | 邀请子 agent + im/task/kb 工具 |
| exam-agent / course-agent / learning-agent / search-agent / remark-agent / user-agent / media-agent / statistics-agent / kb-agent / im-agent / task-agent | sub | 各模块 Feign 封装（`client/` + `tools/`） |

工具封装统一走 `ToolFactory`：声明 `tools: [exam.saveQuestion]` → 前缀映射工具 Bean → `Toolkit.registerTool`（`@Tool` 方法自动生成 JSON Schema）。

## Context 与 Memory

Session 是短期消息来源，MySQL 画像和 ReMe 个人 Wiki 是长期记忆来源，`ContextAssembler`
在每轮调用前统一装配它们。个人 Wiki 是用户拥有、可编辑和可删除的 Markdown 文件，不等同于课程知识库 RAG。

| 层 | 实现 | 存储 |
|---|---|---|
| L1 会话 | `AgentSessionService`（元数据 + Redis 消息，上下文裁剪） | MySQL `agent_session` + Redis |
| L2 画像/习惯 | `ProfileMemoryAdapter`，由行为事件和用户显式画像维护 | MySQL `agent_user_profile/agent_user_behavior` |
| 个人 Wiki | `ReMeMemoryAdapter` → `ReMeHttpJobClient`，检索 Top-5，支持显式写入/更正/删除 | 用户独立 ReMe workspace 中的 Markdown |
| 会话沉淀 | `SessionClosureService` → MySQL Outbox → `ReMeSessionMemoryAdapter` → `/auto_memory` | `agent_memory_outbox` + `session/dialog` 来源 + `daily` 记忆卡片 |

### ReMe 文件原生个人 Wiki

- 使用当前 ReMe Job API：`POST /search|write|edit|delete|auto_memory`，不再依赖旧版 `agentscope-extensions-reme` Java 接口。
- 会话关闭前复制最多 40 条 user/assistant 消息，每条最多 8000 字符；Outbox 入库与 MySQL 会话关闭处于同一事务，事务提交后才清理 Redis 原始消息。
- 后台按批次领取任务，PROCESSING 租约支持实例崩溃接管；ReMe 失败按指数退避，默认 5 次后进入 DEAD。成功后清空 Outbox 消息 payload，只保留投递审计元数据。
- Auto Memory 只生成 `daily` 层；ReMe 默认后台 `dream_cron`/`auto_dream` 再把可复用内容归并到 `digest`。远端调用失败不回滚会话关闭；Outbox 写库失败则保留短期消息并让关闭请求失败，供调用方重试。
- `/write` 按当前契约发送 `path/name/description/content`，而不是自行拼装 frontmatter。
- 首版检索由 ReMe 默认 BM25 + WikiLink 扩展完成，`limit=5`；embedding 不是前置依赖。
- 启用：`LMS_REME_ENABLED=true`，并配置 `LMS_REME_ENDPOINT_TEMPLATE=http://reme-gateway.internal/users/{userId}`。
- 存量数据库需先执行 `docker/mysql/init/92-v04-memory-outbox.sql`；Docker 全新初始化会由 `11-lms-ai.sql` 自动建表。
- 当前 ReMe `/search` 不携带 workspace 参数，因此 endpoint 后的可信代理/服务必须把每个用户路由到独立 workspace；配置不含 `{userId}` 时启动失败。
- ReMe 召回故障时聊天继续使用 MySQL 画像；写入、更正、删除失败会明确返回失败，不会虚假确认成功。
- Wiki 路径只由 LMS 生成（`digest/wiki/{uuid}.md`），客户端无法传物理路径。
- 课程知识库 RAG（`kb.ragChatCourse`，lms-kb 五步流水线）保留为子 agent 出题/答疑依据，不属于记忆层

## 配置要点

- `api-key` 通过环境变量 `DASHSCOPE_API_KEY` 注入
- 模型分级（spec §10）：编排 `qwen-max` / 子 agent `qwen-plus` / 摘要 `qwen-turbo`（`lms.ai.agent.*`）
- 阶段开关（spec §9 可回退）：`lms.ai.agent.enabled` / `lms.ai.im.enabled` / `lms.ai.task.enabled`
- IM 渠道（spec §7）：`lms.ai.im.channel` = `console`（默认，兜底）| `wecom`（需 webhook + 回调密钥）
- 延迟任务（spec §6）：落库 + `@Scheduled` 轮询（默认）；`lms.ai.task.kafka-enabled=true` 可开启 Kafka 事件通知

```bash
# 启动前设置环境变量
set DASHSCOPE_API_KEY=sk-xxxx
```

## 目录结构

```
com/lms/ai/
├── AiApplication.java
├── chat/            # AgentChatService（多轮对话 / IM 回流投递）
├── client/          # 业务模块 Feign 契约（course/exam/learning/search/remark/user/media/statistics/kb）
├── config/          # 属性 + 模型工厂 + Feign 用户头透传
├── controller/      # AgentController / TaskController / ImCallbackController / BehaviorController
├── declaration/     # AgentDeclaration（YAML 声明模型）
├── factory/         # PersonalAgentFactory（按用户实例化）
├── im/              # ImChannel 抽象 + Console/WeCom 渠道 + 回流
├── memory/          # L2 画像（PO/Mapper/Service/LongTermMemory）
├── orchestration/   # 快捷入口编排（ask-teacher/generate-exam/generate-course）
├── registry/        # AgentRegistry（声明注册表）
├── session/         # L1 会话（PO/Mapper/Service）
├── task/            # agent_task + 调度 + Kafka 事件（可选）
├── tools/           # @Tool 工具类 + ToolFactory + 任务上下文桥
└── resources/agents # Agent 声明 YAML（13 个）
```
