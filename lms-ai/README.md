# lms-ai AI 能力服务

> AgentScope Java + 阿里云百炼 DashScope（OpenAI 兼容模式）：**单轮 chat（阶段 0）+ 个人 Agent 体系**（个人 Agent / 子 Agent / 两层记忆[会话+画像] / 邀请制编排 / IM 管道 / 定时任务，spec 见 `docs/PERSONAL_AGENT_SPEC.md`）。

## 职责定位

- 分层：**业务层**（AI 能力 / Agent 运行时）
- 依赖：lms-common（Feign/工具/ORM/Redis/Kafka）、agentscope-core、agentscope-openai-spring-boot-starter、agentscope-extensions-model-openai、agentscope-extensions-reme（ReMe 记忆）
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
| DELETE | `/agent/sessions/{id}` | 结束会话（L1 摘要 → L2） | 登录 |
| GET/POST | `/agent/profile/mine` | 我的画像（L2，「记住我」） | 登录 |
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

## 两层记忆（spec §4 简化：L3 个人知识库不再作为记忆层）

> 设计取舍：L3 知识文档记忆需要用户主动沉淀文档，练手场景意义有限（属过度设计），
> 已从 agent 记忆体系移除；个人知识库的 lms-kb owner 扩展保留为普通业务能力（课程库主场景不受影响）。
> ReMe 从对话轨迹积淀的正是"习惯/偏好/薄弱点"层（L2），与文档 RAG 无关。

| 层 | 实现 | 存储 |
|---|---|---|
| L1 会话 | `AgentSessionService`（元数据 + Redis 消息，上下文裁剪） | MySQL `agent_session` + Redis |
| L2 画像/习惯 | **AgentScope ReMe**（`ReMeMemoryFactory` → `ReMeLongTermMemory`：对话轨迹提取-遗忘-整合-检索注入，workspaceId=`user_{userId}` 一人一记忆空间）；未部署 ReMe 时回退 `ProfileLongTermMemory`（MySQL 画像 + LLM 摘要压缩） | ReMe 服务端 / MySQL `agent_user_profile/agent_user_behavior` |

### ReMe 长期记忆（阿里通义实验室，AgentScope 官方记忆扩展）

- 依赖：`io.agentscope:agentscope-extensions-reme`（Maven Central 1.0.12），实现 AgentScope `LongTermMemory`，经 `ReActAgent.Builder.longTermMemory()` + `STATIC_CONTROL` 模式自动 record/retrieve
- **部署 ReMe 服务端**（二选一，参考官方仓库 [agentscope-ai/ReMe](https://github.com/agentscope-ai/ReMe) 与 [docs.agentscope.io/reme](https://docs.agentscope.io/reme/latest/zh/overview)）：
  1. 本地 Docker 部署（file-native 记忆系统，无需外部数据库），暴露 `POST /retrieve_personal_memory`、`POST /summary_personal_memory`
  2. 通义百炼云服务（托管 endpoint + API key）
- 启用：`lms.ai.agent.reme.enabled=true` + `lms.ai.agent.reme.base-url=http://<host>:<port>`；超时默认 3s（官方默认 60s 偏长，避免记忆检索拖慢对话）
- 回退：未配置 base-url / enabled=false 时自动使用 MySQL 画像记忆（`ProfileLongTermMemory`），对话不受影响
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
