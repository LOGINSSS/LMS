# 个人 Agent 体系与三层记忆编排规范（Spec）

> 状态：**方案积淀中（未实现）** · 日期：2026-08 · 关联代码：`lms-ai/`（Agent 运行时）、`lms-kb/`（知识库与 RAG）、各业务模块 REST（工具面）
>
> 目标：给**每个用户**一个专属个人 Agent；Agent 能积淀用户自己的**知识文档**与**使用习惯**（三层记忆）；Agent 之间通过 AgentScope 的**邀请制互调**协作完成任务；问答/任务可通过 **IM 管道**（微信 / QQ / 企微）触达老师，并支持**定时任务**编排。

---

## 1. 背景与动机

### 1.1 现状：AI 能力是"无记忆的单点问答"

| 能力 | 现状（lms-ai + lms-kb） | 目标（本规范） |
|---|---|---|
| LLM 对话 | ✅ 单轮 chat（AgentScope OpenAI → DashScope） | ✅ 多轮 + 记忆 |
| RAG 问答 | ✅ 课程知识库（lms-kb 五步流水线，**按课程绑定**） | ✅ **按用户**的个人知识库 + 课程库并存 |
| 记忆 | ❌ 无（每次对话无上下文、无用户画像） | ✅ **三层记忆**（会话 / 习惯 / 知识） |
| Agent | ❌ 只有裸模型调用 | ✅ 每个用户一个个人 Agent，各业务模块封装子 Agent |
| Agent 互调 | ❌ 无 | ✅ AgentScope 邀请制（agent-as-tool / event bus / A2A） |
| IM / 定时任务 | ❌ 无 | ✅ 问答同步到老师小助手（微信/QQ/企微）+ 延迟/周期任务 |

**结论**：平台能力（多模块 REST + 知识库 + 网关鉴权）已就绪，缺的是 **Agent 运行时 + 记忆体系 + 编排机制** 这一层。本规范只新增这一层，不动业务模块。

### 1.2 业务需求（原文提炼）

1. **每人一个 agent**：学生有自己的 agent、老师有自己的 agent，agent 会"长成"用户的样子——积淀他本人的知识文档、使用习惯。
2. **三层记忆**：为支撑"越来越懂用户"，需要 会话记忆 / 习惯画像记忆 / 知识文档记忆 三层。
3. **agent 互相调用（邀请制）**：老师 agent 可以调度考试 agent 生成考试大纲、题目；考试 agent 再调用考试模块内的工具完成业务落地。
4. **按角色暴露不同能力**：老师端能用考试 agent（发布任务）、课程 agent（生成大纲 + 章节 HTML）；学生端能调自己的 learning agent（获取学习状态）、向老师 agent 提问。
5. **问答经 IM 管道触达**：学生 agent 发布问答后，老师 agent 把问题同步到 IM 端的老师小助手（微信/QQ/企微），老师可通过 IM 回复。
6. **模块 agent 封装进 AI 模块知识库**：每个有 AI 需求的业务模块封装对应的子 agent（一个声明 + 工具列表），统一放在 lms-ai 的知识库/注册表里。

### 1.3 概念澄清（重要）

- **AgentScope 里子 agent = 一个声明 + 工具列表**：声明（name/description/system prompt/模型）+ 绑定的一组工具（函数工具），不是独立进程。子 agent 通过 `SubAgentTool` 暴露给父 agent，父 agent 在 ReAct 循环里像调工具一样"邀请"子 agent 协作。
- **"邀请制"**：AgentScope Java 提供三条互调通道——① `SubAgentTool`（静态声明，父调子，推荐主用）；② `SubagentEventBus`（子 agent 向父/兄弟广播事件）；③ A2A 协议（跨进程/跨服务 agent 间发现与调用，v2 集成文档，后续演进）。本规范以 ① 为主、② 为辅，③ 留作跨服务演进。
- **记忆不是聊天记录的堆叠**：三层记忆分别解决"这次会话说了什么 / 这个用户长期是什么样 / 用户沉淀了哪些知识"，各有存储与读写时机（见 §4）。
- **IM 管道是"渠道适配"而非"即时通讯系统"**：接入的是企微群机器人/微信客服等开放接口，agent 侧只定义统一 `ImChannel` 接口（见 §7）。

---

## 2. 总体架构

```
                    ┌─────────────────────────────────────────────┐
                    │              lms-web (Vue3)                 │
                    └──────────────────┬──────────────────────────┘
                                       │ /agent/** /ai/**  (JWT)
                    ┌──────────────────▼──────────────────────────┐
                    │         lms-gateway (8080，路由/鉴权)         │
                    └───────┬──────────────────┬──────────────────┘
                            │                  │
              ┌─────────────▼──────────┐  ┌───▼─────────────────────────────┐
              │  lms-ai (8095)         │  │ 业务模块（工具面，Feign 封装）      │
              │  Agent 运行时           │  │ course / exam / learning / search │
              │                        │  │ remark / user / media / statistics│
              │  ┌──────────────────┐  │  └───────────────────────────────────┘
              │  │ Agent 注册表       │  │
              │  │ (声明+工具列表)     │  │
              │  ├──────────────────┤  │
              │  │ 个人 Agent 工厂     │◄─┼── 按用户实例化（TeacherAgent/StudentAgent + 模块子Agent）
              │  ├──────────────────┤  │
              │  │ 编排服务 Orchestrator│ │  SubAgentTool 邀请 / EventBus 广播
              │  ├──────────────────┤  │
              │  │ 三层记忆管理器       │  │  L1 会话 / L2 画像 / L3 知识库
              │  └──────────────────┘  │
              └──────┬─────────┬───────┘
                     │         │ Feign
        ┌────────────▼───┐  ┌──▼──────────────────────┐
        │ lms-kb (8096)  │  │ 基础设施                  │
        │ 知识库+RAG流水线 │  │ Redis(L1/L2) · MySQL(L2/L3元数据) │
        │ (按用户扩展)     │  │ Kafka(任务/事件) · ES(向量)     │
        └────────────────┘  └───────────────────────────┘
   IM 管道（微信/QQ/企微）：agent → 老师小助手推送 / 老师回复 → agent
```

### 2.1 组件职责

| 组件 | 位置 | 职责 |
|---|---|---|
| Agent 注册表 | lms-ai | 各业务模块子 agent 的声明（name/描述/提示词/模型/工具列表）集中存放，可落库或 classpath 资源 |
| 个人 Agent 工厂 | lms-ai | 按 `user_id + 角色` 创建/复用个人 agent 实例（老师、学生），绑定其可用的子 agent 与知识库 |
| 编排服务 Orchestrator | lms-ai | 父 agent 调度子 agent（`SubAgentTool`）、事件广播（`SubagentEventBus`）、任务上下文传递与结果回传 |
| 三层记忆管理器 | lms-ai | L1 会话（Redis/StateStore）、L2 画像（MySQL + 行为事件）、L3 知识（lms-kb 按用户知识库 + RAG） |
| 工具适配层 | lms-ai | 业务模块 REST → `ReflectiveFunctionTool`（Feign 调用），统一工具描述与参数 JSON Schema |
| IM 管道 | lms-ai（可独立成服务） | `ImChannel` 接口 + 企微/微信/QQ 适配器，双向：agent→IM 推送、IM→agent 回流 |
| 任务调度 | lms-ai + Kafka | 延迟/周期任务（问答超时提醒、大纲生成后通知），复用已部署的 Kafka 3.8 |

### 2.2 与现有系统衔接

- **网关**：新增 `/agent/**` 路由 → lms-ai（个人 agent 对话入口）；`/ai/**`、`/rag/**` 已存在。白名单按需放行 IM 回调（带签名校验，见 §7）。
- **Nacos**：新增 `lms-ai.yaml` 配置（agent 声明路径、IM 配置、任务队列 topic、各模块 Feign 开关）；业务模块配置不动。
- **lms-kb 扩展点**：知识库目前 `course_id` 唯一绑定；个人 agent 需要 **`owner_type + owner_id`** 维度（课程库保留，新增个人库）。这是 lms-kb 的最小改造点（见 §4.3）。
- **Kafka**：docker-compose 已就绪但零使用——本规范首个落地场景（任务事件 + 问答 IM 通知），见 §6/§7。
- **依赖关系**：lms-ai 新增依赖 `lms-common`（Feign/工具）、`spring-kafka`（任务）；业务模块无改动，只被 Feign 消费。

---

## 3. Agent 声明与注册（声明式模型）

> 对齐 AgentScope Java：`Agent` 由 name/description/system prompt/模型/工具组成；子 agent 用 `SubAgentTool(SubAgentProvider + SubAgentConfig)` 暴露。

### 3.1 声明结构（示例）

```yaml
# lms-ai/src/main/resources/agents/exam-agent.yaml（模块子 agent 声明）
name: exam-agent
description: 考试专家：负责生成考试大纲、按课程出题、管理题库绑定。当用户要求"出题/考试大纲/试卷"时使用。
role: sub            # personal | sub | tool
model: qwen-plus      # 可单独指定模型
systemPrompt: |
  你是 LMS 平台的考试专家。你能：
  1. 根据课程大纲与知识点生成考试大纲（章节、题型分布、分值）
  2. 生成单选/多选/判断题，并调用题库工具落库
  3. 把题目绑定到业务（课程/考试）
  生成题目必须调用题库工具，禁止凭空捏造题目 id。
tools:
  - exam.saveQuestion        # Feign → lms-exam POST /admin/questions
  - exam.queryBizQuestions    # Feign → lms-exam GET /questions/biz/{bizId}
  - exam.bindToBiz            # Feign → lms-exam POST /admin/questions/{id}/biz
  - course.getDetail          # 需要课程信息时调用
knowledge: exam-kb            # 绑定的知识库（可选）
memory:
  layer1: session             # 会话记忆
  layer2: profile             # 画像记忆（只读）
  layer3: kb                  # 知识文档记忆（检索）
```

```yaml
# teacher-agent.yaml（个人 agent 声明，按用户实例化）
name: teacher-agent
description: 老师专属助手：调度考试/课程 agent 生成教学资源，接收学生问答并转达。
role: personal
model: qwen-max
systemPrompt: |
  你是老师 {teacherName} 的专属助手。你可以：
  1. 调度 exam-agent 生成考试大纲与题目（先出大纲给老师确认，再落库）
  2. 调度 course-agent 生成课程大纲与章节 HTML
  3. 接收学生 agent 的问答请求：简单问题直接答；需要老师人工的，
     通过 IM 管道同步到老师小助手，等老师回复后回传学生
  4. 任务较长时拆成定时任务，完成后通知老师
subAgents:            # 邀请制：暴露给本 agent 的子 agent
  - exam-agent
  - course-agent
  - learning-agent     # 学生学习状态查询
tools:
  - im.pushMessage     # 推送到老师小助手（微信/QQ/企微）
  - task.schedule      # 发布定时任务
knowledge:
  - kb: teacher-kb     # 老师个人知识库（讲义、资料）
  - kb: course-kb      # 平台课程知识库（只读）
```

### 3.2 Agent 注册表

- **存放**：`lms-ai/src/main/resources/agents/*.yaml`（classpath，练手期）+ 可选 `agent_registry` 表（配置化演进）。
- **加载**：启动时扫描解析 → `AgentRegistry`（Map<name, AgentDeclaration>），个人 agent 按 `user_id` 工厂实例化（prompt 模板注入用户名、绑定该用户知识库）。
- **个人 agent 与子 agent 的关系**：个人 agent（teacher/student）是"常驻编排者"，子 agent（exam/course/learning/search/remark/...）是"被邀请者"，按角色白名单决定个人 agent 能邀请哪些子 agent。

### 3.3 工具面清单（现有 REST → 工具）

| 子 Agent | 工具（Feign 封装） | 对应接口 |
|---|---|---|
| course-agent | 建课 / 改课 / 上下架 / 我的课程 / 课程详情 / 选课退课 | lms-course `AdminCourseController` / `CourseController` |
| exam-agent | 建题 / 改题 / 删题 / 题目分页 / 按业务取题 / 绑定业务 | lms-exam `AdminQuestionController` / `QuestionController` |
| learning-agent | 课次列表 / 课次详情 / 上报进度 / 课程进度 / 我的学习统计 / 笔记 CRUD / 问答 / 签到 / 积分明细 / 积分榜 | lms-learning `LessonController` / `LearnStatsController` / `NoteController` / `QaController` / `PointsController` |
| search-agent | 课程搜索 / 兴趣推荐 / 兴趣标签上报 | lms-search `SearchController` / `InterestController` |
| remark-agent | 点赞切换 / 点赞数 / 批量状态 | lms-remark `LikeController` |
| user-agent | 用户详情 / 资料修改 | lms-user `UserController` |
| media-agent | 上传 / 分页 / 详情 / 删除 | lms-media `MediaController` |
| statistics-agent | 看板 / 今日数据 / Top10 | lms-statistics `StatisticsController` |
| kb-agent | 知识库 CRUD / 文档上传 / RAG 问答 / 文档分页 | lms-kb `KbController` / `RagController` |
| im-agent | IM 推送 / IM 回流解析 | 本规范新增（见 §7） |
| task-agent | 定时任务发布 / 查询 / 取消 | 本规范新增（见 §6） |

> 工具封装统一走 `ToolFactory`：Feign Client 方法 → `ReflectiveFunctionTool`（自动生成参数 JSON Schema 与描述），一个子 agent 一组 `ToolGroup`，注册进该 agent 的 `Toolkit`。

---

## 4. 三层记忆设计（核心）

### 4.1 分层总览

| 层 | 名称 | 内容 | 存储 | 生命周期 | AgentScope 映射 |
|---|---|---|---|---|---|
| L1 | 会话记忆（短期） | 当前会话的多轮消息、临时上下文、未完成任务 | Redis（`agent:session:{userId}`，TTL 1~7 天） | 会话级 | `Memory` / `StateBackedMemory` / `AgentStateStore` |
| L2 | 画像与习惯记忆（长期·结构化） | 用户属性、学习习惯、偏好、历史行为摘要、agent 对用户的印象 | MySQL `agent_user_profile` + 行为事件表 | 用户级（持续累积） | `LongTermMemory`（结构化记录）+ 自定义 `StaticLongTermMemoryHook` |
| L3 | 知识文档记忆（长期·非结构化） | 用户自己的知识文档（讲义/笔记/资料），经 RAG 检索增强 | lms-kb 按用户知识库 + ES 向量 | 用户级 | `Knowledge` + `GenericRAGHook`（或 Feign 复用 lms-kb 五步流水线） |

**记忆访问规则**：L1 每次对话自动读写；L2 由行为事件异步沉淀、回答前注入画像摘要；L3 按需检索（agent 自主决定或命中规则触发）。写权限分级：L1 全写，L2/L3 只允许"记忆沉淀工具"写入（防 agent 幻觉污染画像）。

### 4.2 L1 会话记忆

- **实现**：AgentScope `AgentStateStore`（`InMemoryAgentStateStore` 起步，升级 `RedisAgentStateStore`）；消息列表按 `(userId, sessionId)` 隔离。
- **会话管理**：`POST /agent/sessions` 建会话（返回 sessionId），`POST /agent/chat`（带 sessionId）多轮对话，`DELETE /agent/sessions/{id}` 结束。
- **上下文裁剪**：超过模型窗口按"系统 + 画像摘要 + 最近 N 轮"裁剪，历史消息压缩成摘要存入 L2（见 4.3 的 summary 字段）。

### 4.3 L2 画像与习惯记忆

- **表设计（库 lms_ai）**：

```sql
-- 用户画像主表（一人一行，agent 眼中的用户）
CREATE TABLE agent_user_profile (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id BIGINT NOT NULL UNIQUE,          -- lms-auth 用户
  role TINYINT NOT NULL,                   -- 1学生 2老师
  display_name VARCHAR(64),                -- 昵称
  summary TEXT,                            -- 画像摘要：agent 定期压缩生成（习惯/偏好/近期目标）
  interests VARCHAR(512),                  -- 兴趣标签（可同步 lms-search user_interests）
  learning_habits TEXT,                    -- 学习习惯 JSON：活跃时段/学习节奏/擅长薄弱知识点
  created_at DATETIME, updated_at DATETIME, deleted TINYINT DEFAULT 0
);

-- 行为事件流水（L2 的原料：对话/提问/答题/签到/搜索/点赞/文档上传）
CREATE TABLE agent_user_behavior (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  event_type VARCHAR(32) NOT NULL,         -- chat/ask_question/answer/quiz/sign_in/search/like/kb_upload...
  payload JSON,                            -- 事件明细（问题主题/题目知识点/搜索词...）
  create_time DATETIME NOT NULL,
  KEY idx_user_time (user_id, create_time)
);

-- 画像生成任务（L2 摘要的异步计算，周期执行）
CREATE TABLE agent_profile_job (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  status TINYINT DEFAULT 0,                -- 0待执行 1执行中 2完成 3失败
  last_summary_time DATETIME,
  create_time DATETIME, update_time DATETIME
);
```

- **沉淀时机**（行为事件 → 画像）：
  - 事件来源：lms-web 前端行为埋点（经网关转发）或业务模块 Feign 回调（如提问/签到/答题完成）。
  - 异步管道：行为事件 → Kafka（topic `lms-agent-behavior`）→ lms-ai 消费 → 更新 `agent_user_behavior` + 触发画像增量计算；每日/每 N 条事件触发一次 `summary` 全量压缩（LLM 把行为流水压缩成画像摘要）。
  - Agent 侧：回答前 `StaticLongTermMemoryHook` 把 `summary + interests + learning_habits` 注入 system prompt；回答中用户显式说"记住我……"时，调用 `memory.saveProfile` 工具直接更新画像字段。
- **习惯记忆示例**：学生常晚上学习、多次在"数据结构-图"上提问 → 画像记录"薄弱点：图论；活跃时段 20-23 点"，后续 agent 推荐学习内容时优先该薄弱点。

### 4.4 L3 知识文档记忆

- **现状**：lms-kb 知识库按 `course_id` 唯一绑定（`KnowledgeBase.courseId`），RAG 五步流水线（rewrite→HyDE→混合检索→rerank→Qwen-VL 生成）可直接复用。
- **扩展点（lms-kb 最小改造）**：知识库归属从"仅课程"扩展为 **`owner_type(1课程/2用户)` + `owner_id`**：
  - `knowledge_base` 增加 `owner_type` 列；`course_id` 语义保留（owner_type=1 时用），个人库 owner_type=2 + owner_id=user_id。
  - 唯一约束从 `uk_course` 调整为 `uk(owner_type, owner_id)`。
  - `RagRequest` 增加 `ownerType/ownerId` 或直接传 `userId`，`RagService` 按用户查个人库检索。
  - 前端知识库管理页增加"我的知识库"入口（lms-web 已有媒资上传，可复用）。
- **Agent 侧**：kb-agent 封装 `ragChat(userId, question)` 工具；子 agent（如 exam-agent 出题）可先检索课程知识库/个人知识库再生成，保证题目有依据。
- **权限**：个人知识库只允许本人 agent 检索；课程知识库按选课关系（学生已选该课）放行——工具层用 `UserContext` 校验后调用。

### 4.5 三层记忆的读写时序（示例：学生问老师问题）

```
学生: "帮我问老师：KMP 算法为什么这么难？"（L1 记录该会话）
  1. L2 读取画像：学生薄弱点含"字符串匹配"→ 注入上下文
  2. student-agent 邀请 teacher-agent（SubAgentTool）
  3. teacher-agent 检索 L3（老师讲义知识库，可选）
  4. teacher-agent 判断：简单问题直接答 → 答案回传 student-agent → 学生
     复杂问题/需老师人工 → 走 IM 管道（§7）+ 可选定时任务（§6）
  5. 结束：L1 摘要压缩 → 追加进 L2 行为流水（"学生询问 KMP"）
```

---

## 5. 跨 Agent 编排（邀请制互调）

### 5.1 三条互调通道（按需选用）

| 通道 | AgentScope 支持 | 场景 | 推荐度 |
|---|---|---|---|
| `SubAgentTool`（agent-as-tool） | ✅ `SubAgentTool(SubAgentProvider, SubAgentConfig)` | 父 agent 声明子 agent 工具，ReAct 循环里按需"邀请"子 agent 干活，结果作为工具结果回传 | ★★★ 主用 |
| `SubagentEventBus` | ✅ `SubagentEventBus.emit(Event)` | 子 agent 向父/兄弟广播事件（如"题目已落库"、"任务完成"），父订阅驱动后续编排 | ★★ 配合 |
| A2A 协议 | ✅ v2 集成文档（跨服务） | 后续演进：agent 跨服务/跨进程互调、能力发现 | ☆ 预留 |

> **编排原则**：个人 agent 是编排者，子 agent 是被邀请者；编排图在个人 agent 声明里静态可见（`subAgents`），运行期由 LLM 决定调用顺序；所有互调带**任务上下文**（taskId、发起人、超时、允许工具白名单），防止子 agent 越权。

### 5.2 编排场景时序

**场景 A：老师调度考试 agent 生成考试大纲与题目**

```
老师 → teacher-agent: "给《数据结构》出一套期中试卷"
  teacher-agent(编排者)
    ├─ 邀请 course-agent: 取课程详情/大纲（工具 course.getDetail）
    ├─ 邀请 exam-agent: 生成考试大纲（章节覆盖、题型分布、分值）
    │     exam-agent 内部: 检索课程知识库(L3) → LLM 生成大纲 JSON
    │     └─ 调用 exam 模块工具落库? (阶段1: 大纲只返回给老师确认; 阶段2: 落库 question)
    ├─ 老师确认大纲（人工确认点，InterruptControl 或 IM 回复）
    ├─ 邀请 exam-agent: 按大纲逐题生成（单选/多选/判断）
    │     └─ 调用 exam.saveQuestion → lms-exam POST /admin/questions（真实落库）
    │     └─ 调用 exam.bindToBiz → 绑定到该课程 bizId
    └─ 汇总: 返回题目清单+解析，通知老师
```

**场景 B：课程 agent 生成课程大纲与章节 HTML**

```
老师 → teacher-agent: "帮我建一门《Redis 入门》课程"
  teacher-agent
    ├─ 邀请 course-agent: course.addCourse 创建课程壳（落库，下架态）
    ├─ 邀请 course-agent: 生成课程大纲（章节树 JSON，检索知识库增强）
    ├─ 逐章生成 HTML 讲义 → 存为媒资/知识库文档（media-agent 上传 / kb-agent 入库）
    └─ 老师确认后 course.changeStatus 上架
```

**场景 C：学生 agent 问答 → 老师（IM + 定时任务）**

```
学生 → student-agent: "帮我问老师，KMP 的 next 数组怎么推导"
  student-agent
    ├─ 邀请 learning-agent: 查我的学习状态（可附带"已学 3 章，卡在第 4 章"）
    ├─ 邀请 teacher-agent（携带学生上下文 + 问题）
    │     teacher-agent
    │       ├─ 简单可答 → 直接回答，回传
    │       └─ 需老师人工 → im.pushMessage(企微/微信 老师小助手, 问题+学生上下文)
    │                       + task.schedule(延迟 N 小时未回复 → 提醒老师)
    │                       ← 老师 IM 回复 → im 回流 → teacher-agent → 答案回传学生
    └─ 汇总学习建议给 student-agent → 学生
```

**场景 D：学生调 learning agent 获取学习状态**

```
学生 → student-agent: "我最近学得怎么样？"
  student-agent → learning-agent(工具 myLearnStats + 课次进度 + 积分)
  → 画像(L2)补上下文（薄弱点、活跃时段）→ 生成个性化学习建议
```

### 5.3 编排控制与防滥用

- **任务上下文**：`AgentTaskContext`（taskId、发起 user、父 agent、超时、允许工具白名单）贯穿互调，子 agent 工具调用统一经 `ToolExecutionContext` 注入。
- **深度与超时**：互调深度 ≤ 3（个人 agent → 子 agent → 子 agent 工具），单任务超时 60s~5min 按场景配置，超时返回部分结果。
- **人工确认点**：高副作用动作（落库建题、上架课程、对外推送）前置 `InterruptControl` 或 IM 确认；阶段 1 全部"先生成后确认"。
- **幂等**：工具落库遵循业务模块已有幂等（如选课幂等、题目绑定 upsert）；重试按 taskId 去重。

---

## 6. 定时任务与事件

### 6.1 任务模型

```sql
CREATE TABLE agent_task (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  task_id VARCHAR(64) NOT NULL UNIQUE,     -- 业务幂等键（可 UUID）
  owner_id BIGINT NOT NULL,                -- 归属用户（老师/学生）
  agent_name VARCHAR(64),                  -- 发起 agent
  task_type VARCHAR(32) NOT NULL,          -- qa_remind / outline_generate / report_xxx ...
  payload JSON,                            -- 任务参数（问题、课程、回复地址...）
  trigger_type TINYINT NOT NULL,           -- 1延迟执行 2周期执行 3一次性
  delay_seconds INT, cron VARCHAR(32),     -- 延迟秒数 / 周期 cron
  status TINYINT DEFAULT 0,                -- 0待执行 1执行中 2完成 3失败 4取消
  result TEXT,                             -- 执行结果（agent 输出）
  execute_time DATETIME, finish_time DATETIME,
  create_time DATETIME, update_time DATETIME, deleted TINYINT DEFAULT 0
);
```

### 6.2 执行机制（复用 Kafka）

- **延迟任务**：`task.schedule` 工具写入 `agent_task`（status=0）→ 发 Kafka（topic `lms-agent-task`，消息带 `executeTime`）→ lms-ai 定时消费（或 Kafka 延迟队列/落库轮询）到点执行 → agent 编排 → 结果回写 + 通知。
- **周期任务**：`@Scheduled` 扫描 cron 匹配的 `agent_task` 触发。
- **补偿**：执行失败 status=3 + 重试计数，超限进死信；执行完通知发起 agent（EventBus）或 IM（若配置）。
- **首个场景**：学生问答 → 老师 2 小时未回复 → 提醒老师（IM 推送）；考试大纲生成（长任务）完成 → 通知老师。

---

## 7. IM 管道（微信 / QQ / 企微）

### 7.1 AgentScope 的 IM 集成形态

AgentScope 官方在消息平台/渠道（channel）层支持把 agent 接入 IM 平台（钉钉/飞书/微信等），agent 以"渠道账号"形态收发消息。**练手落地我们不引入 AgentScope 渠道服务端**，而是按同一抽象自己实现 `ImChannel` 适配（接入成本更低、可控）：agent 侧只依赖接口，具体平台适配器可插拔。

### 7.2 接口抽象

```java
public interface ImChannel {
    /** 推送一条消息到目标（老师小助手） */
    ImSendResult push(ImMessage msg);
    /** 平台回调解析为内部消息（IM → agent 回流） */
    ImMessage parseCallback(Map<String, Object> payload);
    /** 平台类型：wecom / wechat / qq */
    String platform();
}

public record ImMessage(
    String to,            // 老师/机器人标识
    String text,          // 文本内容
    String taskId,        // 关联 agent 任务（回流时定位会话）
    String sessionId,     // 关联 agent 会话
    Long userId) {}       // 关联用户
```

- **实现**：`WeComChannel`（企微群机器人 webhook + 接收回调）、`WechatChannel`（微信客服/公众号）、`QqChannel`、`ConsoleChannel`（练手兜底：只打日志/存库，无真实平台也能跑通全链路）。
- **配置**：Nacos `lms-ai.yaml` 声明启用哪个 channel + webhook 地址 + 回调密钥。

### 7.3 双向流程

```
【推】student-agent 提问 → teacher-agent
   → im.pushMessage(teacher小助手, "学生{张三}问: KMP 的 next 数组怎么推导？
      学生背景: 已学3章，进度60%") → WeCom webhook → 老师微信收到
【回】老师在企微回复 → WeCom 回调（验签）→ lms-ai im-回调接口
   → 按 taskId/sessionId 定位会话 → 投递回 teacher-agent
   → teacher-agent 组织答案 → 回传 student-agent → 学生端可见
【定时】2h 未回复 → task-agent 触发 → im.pushMessage 提醒老师
```

- **安全**：回调接口必须验签（平台签名 + 时间戳防重放），放网关白名单但经 lms-ai 校验。

---

## 8. 接口契约（新增部分）

### 8.1 个人 agent 对话（网关 /agent/**）

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/agent/chat` | 个人 agent 多轮对话 `{sessionId?, text, agentType: student|teacher}` → `{sessionId, reply, sources?, taskId?}` |
| POST | `/agent/sessions` | 建会话 |
| GET | `/agent/sessions/mine` | 我的会话列表 |
| DELETE | `/agent/sessions/{id}` | 结束会话（触发 L1 摘要→L2） |
| GET | `/agent/profile/mine` | 我的画像（L2，可编辑"记住我"字段） |
| POST | `/agent/ask-teacher` | 学生快捷入口：学生问题 → student-agent → teacher-agent（含 IM/定时任务编排） |
| POST | `/agent/teacher/generate-exam` | 老师快捷入口：调度 exam-agent 生成大纲/题目 |
| POST | `/agent/teacher/generate-course` | 老师快捷入口：调度 course-agent 建课+大纲+章节 HTML |

### 8.2 IM 回调

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/agent/im/callback/{platform}` | 平台消息回调（验签），回流到对应 agent 会话 |

### 8.3 任务

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/agent/tasks` | 发布任务（agent 内部工具/管理端） |
| GET | `/agent/tasks/{taskId}` | 任务状态与结果 |
| POST | `/agent/tasks/{taskId}/cancel` | 取消 |

---

## 9. 落地分期（每阶段可验收、可回退）

| 阶段 | 内容 | 验收标准 |
|---|---|---|
| **0（现状基线）** | lms-ai 单轮 chat + lms-kb 课程 RAG 可用 | 现有 `/ai/chat`、`/ai/chat/rag` 正常 |
| **1（Agent 骨架）** | Agent 注册表 + 声明解析 + 个人 agent 工厂 + L1 会话记忆（Redis）；`/agent/chat` 走 agent（teacher/student 各一个最小 agent）；lms-kb 支持 owner_type 个人库（L3 最小可用） | 学生/老师各一个 agent 能多轮对话；"我的知识库"上传文档后可 RAG |
| **2（模块子 agent + 工具面）** | ToolFactory 封装各模块 Feign → 子 agent（course/exam/learning/search/remark/user/media/statistics/kb）；子 agent 声明与工具注册 | 对话中能触发真实工具调用（如"查我的积分""搜 Redis 课程"）落库/返回真实数据 |
| **3（编排·邀请制）** | `SubAgentTool` 互调 + EventBus + 任务上下文；场景 A/B/D 打通；L2 画像（行为事件 → Kafka → 画像摘要注入） | 老师一句话让 exam-agent 出题并落库；课程 agent 生成大纲+章节 HTML；学生 agent 查学习状态得到个性化建议 |
| **4（IM + 定时任务）** | `ImChannel` 适配器（企微优先 + Console 兜底）+ 回调回流 + `agent_task` + Kafka 任务；场景 C 全链路 | 学生提问 → 老师企微小助手收到 → 老师回复 → 学生端收到答案；2h 未回复自动提醒 |
| **5（加固）** | 防滥用（深度/超时/人工确认）、权限（L3 按用户/选课）、画像摘要周期压缩、评估（问答质量采样复用 eval 闭环）、流式输出 | 全场景 E2E 通过，超时/越权有兜底 |

> 每阶段可回退：阶段开关配置（`lms.ai.agent.enabled` / `lms.ai.im.enabled` / `lms.ai.task.enabled`），关闭即回到上一阶段能力。

---

## 10. 风险与对策

| 风险 | 影响 | 对策 |
|---|---|---|
| 子 agent 越权/幻觉落库 | 脏数据（乱建题/乱上架） | 高副作用工具前置人工确认；工具参数严格 Schema；阶段 1~2 只读工具先行 |
| L2 画像被污染（agent 记忆错误印象） | 画像失真、推荐错误 | 画像只由行为事件+显式"记住"写入；summary 生成走独立 LLM 调用 + 定期人工可编辑 |
| 长任务编排超时/中断 | 任务悬空 | taskId 幂等 + agent_task 状态机 + 超时补偿 + 结果通知 |
| IM 回调安全 | 伪造消息 | 平台签名验签 + 时间戳防重放 + 回调只做"投递"不做高副作用动作 |
| lms-kb owner_type 改造影响现有课程库 | 课程 RAG 回归 | 兼容迁移：旧数据 owner_type=1 + course_id；新检索按 owner 分支；保留课程库路径 E2E |
| AgentScope Java v2 API 细节差异 | 实现返工 | 阶段 1 先做最小 agent 验证 `SubAgentTool`/`LongTermMemory` API 后再铺开（见附录核对清单） |
| LLM 成本（画像压缩/多 agent 调用） | 费用上涨 | 画像压缩低频（每日/每 N 事件）；子 agent 只在需要时邀请；模型分级（qwen-turbo 摘要 / qwen-max 编排） |

---

## 11. 待确认清单（下一步讨论）

- [ ] lms-kb 个人知识库归属：`owner_type + owner_id` 扩展（推荐）vs 独立库 lms_kb_user？
- [ ] IM 渠道优先级：企微（推荐，webhook+回调最简）> 微信 > QQ；是否先 ConsoleChannel 跑通链路？
- [ ] 延迟任务实现：Kafka 延迟队列 vs 落库 + @Scheduled 轮询（练手建议后者，Kafka 仅做事件通知）？
- [ ] 子 agent 互调：阶段 3 用 `SubAgentTool` 静态声明（推荐）还是先验证 `SubagentEventBus` 事件驱动？
- [ ] 高副作用操作（建题/上架）的人工确认形态：站内确认页 vs IM 确认？
- [ ] L2 行为事件埋点来源：lms-web 前端埋点 vs 业务模块 Feign 回调（推荐后者，可信度高）？

---

## 附录 A：AgentScope Java v2 API 核对清单（jar 实测）

基于本地 `agentscope-core-2.0.2.jar` 反查类清单，确认以下能力存在，实现前需再对具体签名做一次编译验证：

- `io.agentscope.core.ReActAgent` — 主 Agent 类型
- `io.agentscope.core.tool.subagent.SubAgentTool / SubAgentProvider / SubAgentConfig` — 子 agent 互调（agent-as-tool）
- `io.agentscope.core.agent.SubagentEventBus` — 子 agent 事件广播
- `io.agentscope.core.memory.LongTermMemory`（`record(List<Msg>)` / `retrieve(Msg)`）+ `LongTermMemoryMode`（AGENT_CONTROL / STATIC_CONTROL / BOTH）+ `LongTermMemoryTools` + `StaticLongTermMemoryHook` — 长期记忆
- `io.agentscope.core.rag.Knowledge / GenericRAGHook / KnowledgeRetrievalTools / RAGMode` — 内置 RAG
- `io.agentscope.core.tool.*`（`ReflectiveFunctionTool` / `ToolGroup` / `Toolkit` / `ToolRegistry` / MCP 工具）— 工具系统
- `io.agentscope.core.state.AgentStateStore`（`InMemoryAgentStateStore` / `JsonFileAgentStateStore`）— 会话/状态持久化
- `io.agentscope.core.interruption.InterruptControl` — 人工介入点
- `io.agentscope.core.event.*`（`SubagentExposedEvent` 等）— 编排事件
- 官方 v2 集成文档另有：A2A 协议（跨服务 agent 互调）、channel 渠道（IM 接入）、Nacos 集成
