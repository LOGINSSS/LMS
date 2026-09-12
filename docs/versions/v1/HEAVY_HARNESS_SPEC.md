# 重型 Harness 详细设计（HEAVY_HARNESS_SPEC v2）

> 状态：**P0✅ P1✅（含 P3 网关壳化：GuardedFunctionTool 同名壳替换，agent 已无直注业务工具）
> · P2 意图层✅（规则路由 + LLM 分类层【qwen-turbo，intent-llm-enabled 开关】+ ctx + prompt 提示 + Trace）
> · P2b-① AI 自测 KB 化✅ · P2b-② 角色矩阵 exam.*:2 ✅
> · 出卷流程骨干（exam_paper 快照 + 服务端确定性判分）✅ · 发布物排期（exam_schedule：作业/考试 biz_type + 窗口交卷）✅
> · P4 ✅（会话级 writes/invites 计数上限 + LLM 摘要压缩：ContextCompressor 阈值触发 qwen-turbo 摘要注入 + trimToKeep，summary-enabled=false 回退 trimHalf）
> · P5 会话级任务板 ✅（91-v03-agent-task.sql + turn/邀请/写工具落板 + GET /agent/tasks/tree + 管道节点经 SessionLink 挂会话 action=pipeline，任务树含诊断→规划→习题→测评节点）
> · lms-learning 做题测评日上限 ✅ · 作答记录回流 ✅（lms-exam Feign→lms-learning，作业 source=3 / 考试 source=4，
>   UserInfoFeignConfig 透传作答学生身份；ExerciseRecord 新增来源常量）**
> 待推进：P2 LLM 分类层、作业/考试前端（日历/答题/考试专用锁页）与 Kafka 提交轨、作答记录回流 learning、P5 会话级任务板。
> 按 §10 路线推进
> 前身：`docs/GLOBAL_HARNESS_SPEC.md`（方向 A 轻量 Harness，已实现）
> 关联代码：`lms-ai`（chat/declaration/registry/tools/harness/session/task/orchestration 包）
>
> 一句话：**保留 AgentScope 邀请制内核（ReAct Agent 外壳 + 模型适配），在其外围自研五层重型管控——
> 意图路由 / 编排引擎 / 调度层 / 工具执行层 / 安全硬网关。工具不接外部 MCP，全部为业务 Feign 原子操作，统一走执行网关。
> 安全底线始终是确定性代码（PolicyEngine），prompt 只降概率。**

---

## 1. 背景与目标

### 1.1 从方向 A 到重型

方向 A（`GLOBAL_HARNESS_SPEC`）已实现：邀请前置拦截（深度/白名单/高危 HITL）+ 会话生命周期 + Trace。
但存在四个被点名的工程洞（用户评审清单）：

1. **意图识别隐式**：路由全靠父 agent ReAct 自由发挥，无显式意图层，无效果度量；
2. **HITL 只覆盖"高危 agent 邀请"**：工具级写操作（teacher 直调 `im.pushMessage`、learning-agent 直调 `reportProgress`/`submitExercise`…）无确认，`require-confirm=false` 是空洞；
3. **工具面与 Agent 1:1 耦合**：专家直接持有业务工具 Bean，无原子注册表/无统一执行网关/无 readWrite 强制分流/无运行时开关；
4. **编排层零散**：turn/预算散在 AgentChatService，任务板只是"定时任务落库"，无会话级任务树与压缩触发。

### 1.2 目标架构（对应评审 7 条清单）

| 评审清单项 | 本设计落点 | 层 |
|---|---|---|
| 1 意图识别/是否微调 | L0 IntentRouter（显式两层识别 + 效果度量 + 处置阶梯） | L0 意图路由 |
| 2 HITL 在哪体现 | L4 PolicyEngine：动作级 ask（邀请 + 高危写工具） | L4 安全硬网关 |
| 3 用户级还是 agent 级权限 | 身份=用户级（透传+下游业务鉴权），能力=会话级过滤，双层 | L4 安全硬网关 |
| 4 编排引擎（turn/token/任务板/压缩） | L1 TurnLoopEngine + BudgetService + TaskSystem + ContextCompressor | L1 编排引擎 |
| 5 剥夺 agent 直调工具 | L2 调度层：工具壳化，全部经 ToolGateway | L2 调度层 |
| 6 工具执行层（注册表/MCP/schema/读写开关） | L3 ToolRegistry + ToolGateway + 集中 Schema + ToolSwitch；MCP 仅留 SPI | L3 工具执行层 |
| 7 安全硬网关 deny-ask-allow/沙箱/过滤 | L4 PolicyEngine（覆盖邀请+工具两层），沙箱留 SPI | L4 安全硬网关 |

### 1.3 设计原则

- **D-保留内核**：AgentScope 只做 ReAct 循环 + Agent 外壳 + 函数调用机制；不重写 turn 循环为自研运行时（决策见 §2.1）；
- **D-安全靠确定性代码**：所有拦截收敛在 PolicyEngine 一条链，prompt 不承担安全责任；
- **D-动作级覆盖**：被管控的动作 = 邀请 agent + 调用写工具 + 调度任务，不再只拦"邀请"；
- **D-身份与能力分离**：身份（谁在操作，userId/userType）由 RuntimeContext 透传、下游业务服务最终鉴权；能力（能调什么）由 意图∩声明∩会话过滤 三重决定；
- **D-每层可独立回退**：全部动作过配置开关（`lms.ai.harness-v2.*`），关掉某层即回退上一阶段行为，保证练手/联调不断线。

---

## 2. 关键架构决策

| # | 决策 | 结论 | 理由 |
|---|---|---|---|
| D1 | 编排内核 | **保留 AgentScope**（ReActAgent/SubAgentTool/Toolkit），自研五层管控在外围 | 现有 PersonalAgentFactory/管道/17 个 yaml 资产全保留；重写运行时收益延迟、风险巨大（AGENTSCOPE_INVITE_SUMMARY §四 已否 CC 重写路线） |
| D2 | 工具形态 | **不做外部 MCP**；工具 = 业务微服务 Feign 原子操作，统一过 ToolGateway；MCP/File/Command 只留 ToolExecutor SPI 扩展点 | Agent 只服务 LMS 系统业务（工具即微服务能力），无通用外部能力消费方；MCP 引入沙箱/协议复杂度，教育域收益≈0。详见 §7.1 |
| D3 | HITL 模型 | 从"高危 agent 邀请"升级为**动作级**：`(sessionId, actionType, actionId)` 请求/批复缓存 | 堵住"专家把自己业务做成直调工具就绕过确认"的洞（§8.5） |
| D4 | 任务板 | **扩展现有 `agent_task`** 为会话级任务板（MySQL+状态机+轮询，Kafka 可选通知），不引独立 worker 存储 | 复用 TaskService/Scheduler/幂等键；练手期一致性与部署成本最低（用户已确认） |
| D5 | 意图识别 | 两级：确定性规则层（快捷入口/正则）→ LLM 分类层（qwen-turbo + JSON Schema 约束 + few-shot）；**不微调**，预留分类器微调点 | 教育域意图集合固定、专家面有限；先量化（Intent Trace）再决定是否训练（§4.6） |
| D6 | 剥夺直调的实现方式 | 不拆 AgentScope 注册机制：ReAct toolkit 注册"**网关壳工具**"（同名/同描述），内部全部转 `ToolGateway.execute` | LLM 视角工具名不变、function-calling 不受影响；agent 不再持有任何业务实现 = 剥夺成立且改动收敛在 ToolFactory |
| D7 | 上下文压缩 | 轮次/预算/任务规模触发 → qwen-turbo LLM 摘要（升级现状 trimHalf）+ L2 画像联动 | 复用 summary-model 分级，压缩点由任务板与预算驱动（§5.5） |
| D8 | 身份鉴权位置 | **保留现状**：身份仍透传下游业务服务做最终校验（如 lms-exam `assertTeacher`），L4 只做"前置快速拒绝 + 动作级确认"，不做重复鉴权 | 单点收权会重写全部业务模块；双层 = 网关前置拦截 + 下游兜底 |

---

## 3. 总体架构与调用链

```
用户 /agent/chat
  └─ L1 TurnLoopEngine.chat(user, sessionId, text)           [替换 AgentChatService 主流程]
       ├─ BudgetService.checkBeforeTurn()                    轮/token/写调用预算
       ├─ L0 IntentRouter.route(...) → IntentDecision        意图 + 建议能力面（低置信→NULL 兜底自由 ReAct）
       ├─ ContextAssembler：L1 消息(L2 摘要触发判定) + L2 画像 + 意图提示
       ├─ L2 调度：ToolFactory.build(decl, intent)           注册【网关壳工具】，仅当意图面∩声明面
       ├─ ReActAgent.call(...)                                AgentScope ReAct 循环
       │     ├─ 想调业务工具 → 网关壳 → L3 ToolGateway.execute(toolId, args, ctx)
       │     │      └─ L4 PolicyEngine.decide(ACTION_CALL_TOOL, ...) → ALLOW / DENY / ASK(HITL)
       │     │           → ALLOW：ToolExecutor 执行（Feign→业务微服务）
       │     └─ 想邀专家   → GuardedAgentTool（升级）→ L4 PolicyEngine.decide(ACTION_INVITE, ...)
       │           → ALLOW：委托原生 SubAgentTool
       ├─ BudgetService.recordTurn / TraceService.record      记账 + 动作级审计
       └─ TaskSystem：turn 任务 + 子任务树落库，压缩/续跑触发
```

**关键语义**：LLM 永远只"看到"工具名与描述（壳）；真实执行、鉴权、确认、记账全部发生在 L3/L4 的确定性代码里。这就是"剥夺直调"的实现方式（D6）。

---

## 4. L0 意图路由层（清单 Q1）

### 4.1 定位

意图识别从"父 agent ReAct 隐式判断"升级为**显式前置层**：输入用户消息，输出结构化意图决策，
供三处消费：①注入 system prompt 提示当前意图；②**预收窄能力面**（意图→允许工具面，喂 L4 过滤，降低误选面）；③效果度量（后续是否微调的数据源）。

### 4.2 两级识别

```
IntentRouter.route(userId, userType, sessionId, text, agentType)
  ├─ ① 确定性规则层（零成本，优先）
  │     - 快捷入口直通：文本前缀/正则命中 askTeacher/generateExam/generateCourse 场景
  │       → 直接走 OrchestratorService（现状已存在，绕开自由 ReAct）
  │     - 会话状态继承：上轮 intent 未完成（如 HITL 批复后"继续"）→ 沿用上轮
  ├─ ② LLM 分类层（qwen-turbo，成本低）
  │     - prompt：角色上下文 + intent 枚举定义(含触发条件/负例) + 最近 1 条用户消息
  │     - 输出：JSON Schema 约束 { intent: enum, targetAgent?: enum, confidence: 0-1, directAnswer?: bool }
  │     - few-shot：每 intent 1 例（从 17 个 agent description + 历史会话提炼）
  └─ ③ 兜底：LLM 失败 / 置信 < 阈值 / schema 校验不过
        → IntentDecision.NULL → 行为 = 现状自由 ReAct（只多记一条 trace）
```

### 4.3 Intent 枚举初始表（教育域，可扩）

| intent | 语义 | 建议 agent 面 | 说明 |
|---|---|---|---|
| `DIRECT_CHAT` | 闲聊/角色问答 | （无工具） | 直答不调工具 |
| `QUERY_LEARNING` | 我学得怎么样/进度/积分 | learning-agent（读面） | |
| `NOTE_SIGN_QA` | 笔记/签到/问答 | learning-agent（写面，risk 分级） | |
| `DIAGNOSE_PLAN` | 帮我诊断/规划学习 | learning-agent（pipeline.diagnose/plan） | 固定管道 |
| `EXERCISE_ASSESS` | 练习/测评/交卷 | learning-agent（pipeline.exercise/assess） | 写回流，动作级 ask |
| `GENERATE_EXAM` | 出题/考试大纲/试卷 | exam-agent | 高危邀请（现状 HITL 保留） |
| `GENERATE_COURSE` | 建课/大纲/章节 | course-agent | 高危邀请 |
| `ASK_TEACHER` | 向老师提问 | student→teacher-agent | 快捷入口直通 |
| `TEACHER_IM` | 推消息给老师小助手 | teacher 面 im.pushMessage | **工具级高危，动作级 ask（补洞）** |
| `KB_RAG` | 知识库问答 | kb/rag 面 | |
| `TASK_MANAGE` | 定时任务管理 | task.schedule/query/cancel | 排程动作进任务板 |
| `MEDIA_FILE` | 上传/删除文件 | media-agent | 删除 = 高危写 |

> 映射表存配置或 `IntentDeclaration` 类（常量+映射），不进 LLM prompt 全量（只给枚举定义）。
> agentType（student/teacher）先过滤一半枚举，减少分类空间。

### 4.4 意图预收窄（Intended Capability）

`IntentDecision` 携带 `allowedToolIds` / `allowedAgentInvites`，在 L2 构建 toolkit 时**只注册意图允许的壳**，
在 L4 作为会话级过滤一层。效果：即使 LLM 后续幻觉换工具，壳集合里根本没有 → 无法触发（确定性）。

### 4.5 效果度量（为"要不要微调"提供数据）

- Intent Trace 事件：`intent_route`（输入 hash、结果 intent、置信、兜底标志、最终路由到哪个 agent/工具）；
- 指标：意图命中率（intent 建议的 agent 是否真被调用/用户是否满意）、兜底率、误路由率（复用 `DENY_WHITELIST`）；
- 入口：`GET /agent/harness-v2/intent/stats?sessionId=`（归属人）。

### 4.6 "效果不好"的处置阶梯（回应 Q1）

1. 看 Intent Trace 量化（兜底率/误路由率），区分"识别错"与"LLM 没按意图走"；
2. 枚举定义与 description 加负例（最大杠杆）；
3. few-shot 增补错误样本（把 Trace 里兜底样本转成反例）；
4. 收紧 schema/阈值；
5. **最后才考虑**：独立轻量意图分类模型 LoRA 微调（qwen-turbo 级，只输出枚举），
   训练数据 = Intent Trace + 人工标注；**不微调对话/编排主模型**。

---

## 5. L1 编排引擎层（清单 Q4）

### 5.1 TurnLoopEngine（接管 AgentChatService 主流程）

`chat/AgentChatService` 收敛为薄入口，逻辑下沉：

```
chat()
  1 sessionGuard 职责 → BudgetService.checkBeforeTurn(sessionId)
  2 IntentRouter.route() → intent
  3 sessionService.loadMessages + ContextCompressor.maybeCompress(sessionId, ctx)   // §5.5
  4 toolFactory.build(decl, intent) → 意图收窄后的壳 toolkit
  5 agent.call(messages, ctx)
  6 BudgetService.recordTurn/recordAssistant
  7 TaskSystem.recordTurn(sessionId, userId, intent, taskCount)                     // §5.3
  8 HarnessTraceService.record(...)
```

### 5.2 BudgetService（升级 HarnessSessionGuard）

- 维度扩展：`turns` / `tokens`（估算）/ `writeCalls`（写工具调用次数）/ `invites`；
- 存储：Redis hash `agent:usage2:{sessionId}`（保留旧键兼容，双写后迁移）；
- 上限配置 `session.max-*`，超限策略分档：`terminate`（现状）| `compress`（触发摘要后继续）| `direct-answer-only`（降级）；
- 每档可配，`0`=不限。

### 5.3 TaskSystem 会话级任务板（扩展 agent_task）

**表扩展 DDL（新增列，`ALTER` 平滑升级，不重建表）**：

```sql
ALTER TABLE agent_task
  ADD COLUMN session_id      BIGINT       NULL COMMENT '归属会话（turn/子任务关联）',
  ADD COLUMN parent_task_id  BIGINT       NULL COMMENT '父任务 id（任务树）',
  ADD COLUMN intent          VARCHAR(32)  NULL COMMENT '触发意图（L0 输出）',
  ADD COLUMN action_type     VARCHAR(16)  NULL COMMENT '动作类型: turn/invite/tool/pipeline/timer',
  ADD COLUMN action_ref      VARCHAR(64)  NULL COMMENT '动作引用: invite:exam-agent / tool:submitExercise',
  ADD COLUMN artifacts       TEXT         NULL COMMENT '中间产物 JSON（管道节点输出/工具结果摘要）',
  ADD COLUMN depth           INT          NULL DEFAULT 0 COMMENT '任务树深度（对齐邀请深度）';
CREATE INDEX idx_agent_task_session ON agent_task(session_id, status);
CREATE INDEX idx_agent_task_parent ON agent_task(parent_task_id);
```

**任务树语义**：

```
turn-任务 (action_type=turn, intent=DIAGNOSE_PLAN)            ← 每轮用户消息一条
  └─ pipeline-子任务 (action_type=pipeline, task_type=diagnose_report)
       └─ tool-子任务 (action_type=tool, action_ref=tool:getDiagnosis)   ← 每次写工具/读工具落一条(只写级别可配置)
timer-任务 (action_type=timer, task_type=qa_remind)           ← 现状定时任务挂到会话树
```

**TaskService 扩展 API**（现状 schedule/query/cancel 保留）：
- `recordTurn(sessionId, ownerId, agentName, intent, textHash)` → 顶层 turn 任务；
- `recordAction(sessionId, parentTaskId, actionType, actionRef, status, result)` → 子任务/动作；
- `markDone / markFailed(taskId, artifacts, result)`；
- 查询：`tree(sessionId)`（前端任务面板）、`latestTurn(sessionId)`。

**状态机**：沿用 0待执行→1执行中→2完成/3失败/4取消，动作型任务即时流转（0→1→2 同事务）。

### 5.4 任务板驱动的管道（LearningPipelineService 改造）

现状：Java 顺序调用 runNode 四个节点、产物以字符串在方法间传递（`LearningPipelineService.diagnose/plan/exercise/assess`）。
改造：每节点 = 一个 `pipeline` 类型任务，父 = 上一节点任务，产物（诊断报告/路径/习题包/评估）写 `artifacts`；
节点重启/续跑从任务板取父产物，天然可断点续跑与审计（对齐"任务板管 turn 流转、任务状态"）。

### 5.5 上下文压缩触发（ContextCompressor）

- 触发点（任一）：
  1. L1 消息数 > N（现状 trimHalf=40 升级为 LLM 摘要，阈值可配）；
  2. BudgetService 预算接近上限且策略=compress；
  3. 任务板 turn 树规模超限；
- 动作：把最近消息经 qwen-turbo（`summary-model`）压成结构化摘要（结论/待办/未完成任务引用），
  摘要写入 L1 key（`agent:session:{sid}:summary`）替代最旧一半，并联动 L2 画像（ProfileService 记录摘要事件）；
- 失败降级：回退现状 trimHalf，不阻塞对话。

### 5.6 QueryEngine（统一查询入口）

概念收敛：会话内一切查询（前端 `/agent/sessions` 消息、任务板 tree、Trace、usage、意图统计、画像摘要）走一个服务，
缓存 Redis 消息 + MySQL 任务 + 画像，避免各 Controller 自行拼装。轻量实现，不引 OLAP。

---

## 6. L2 调度层（清单 Q5）

### 6.1 能力面模型

Agent 声明（yaml）语义升级：`tools` 不再直接映射"业务工具 Bean"，而是声明**能力面（capability ids）**：

```yaml
# 例 teacher-agent
capabilities:            # 可选声明（不声明=按 role 默认）
  domains: [im, task]    # 域级：能调 im.*、task.* 域
```

工具名/描述仍由 ToolRegistry 按域解析生成 → 壳注册（§6.2）。17 个 yaml 只改 `tools` 字段写法，
系统 prompt/description/subAgents 不动（避免动 prompt 资产）。

### 6.2 网关壳工具（剥夺直调的实现）

`ToolFactory` 改造（核心，改动收敛点）：

```java
// 现状：toolkit.registerTool(业务Bean) —— 业务实现进 ReAct
// 重型：toolkit.registerTool(壳) —— 同名同描述，内部转网关
ToolShell shell = ToolShell.builder()
    .toolId("exam.saveQuestion")
    .declaration(registry.meta("exam.saveQuestion"))   // 元数据：schema/readOnly/risk
    .build();
// AgentScope 调用壳 → shell.call → ToolGateway.execute(toolId, argsJson, ctx)
```

- LLM 看到的工具名/描述/参数 schema 与现状一致（function-calling 无感知）；
- agent/专家代码里**不再有任何业务实现**（业务实现 = ToolExecutor，在 L3）；
- 壳注册数量由 `意图∩声明域` 决定（L0 预收窄落地处）。

### 6.3 邀请统一网关

`GuardedAgentTool` 保留（邀请入口唯一接管点不变），但内部决策从 `DefaultAgentInviteGuard` 迁移到
`PolicyEngine.decide(ACTION_INVITE, …)`（§8），HITL/深度/白名单逻辑成为策略链上一条规则，不另起炉灶。

### 6.4 调度三重约束

```
可调度目标 = 意图建议面(§4.4)  ∩  声明域(§6.1)  ∩  PolicyEngine 会话过滤(§8.2)
```
三层都是确定性集合运算，LLM 只在最终集合内选（函数调用机制天然只触发已注册壳）。

---

## 7. L3 工具执行层（清单 Q6）

### 7.1 回答：需不需要外部 MCP？

**不需要。** 结论理由：

1. 你们 Agent 的全部消费方是 **LMS 系统业务**：工具 = 业务微服务能力的 Feign 原子封装
   （lms-exam/lms-course/lms-learning/…），这些能力已经"封装好"且带服务端鉴权；
2. MCP 解决的是**通用外部能力接入**（文件系统、命令执行、网页抓取、第三方 API 的标准化桥），
   你们没有这类消费方，且一旦引入文件/命令面就必须配套沙箱与危险动作清单（成本高、无收益）；
3. "工具全封装然后 Feign 调用" 正是本设计 D2：**统一 ToolGateway + Feign 执行器**，工具元数据集中管理。

**扩展点（预留不实现）**：`ToolExecutor` SPI —— 未来若要 agent 读上传文档/接第三方，
实现 `FileToolExecutor` / `McpToolExecutor` 注册进注册表即可，L3/L4 无需改动。

### 7.2 原子工具注册表 ToolRegistry

```java
@LmsTool(id = "exam.saveQuestion", domain = "exam",
         readOnly = false, risk = Risk.HIGH,
         desc = "新建题目（老师），返回题目 id…")
// 原 ExamTools.saveQuestion 方法体迁到独立 Executor（或保留类内，注解扫描收集）
```

- 启动扫描收集 `ToolExecutor` Bean → 元数据表（含 **集中 JSON Schema**：必填/类型/枚举/范围，
  替代散落 `@ToolParam`；schema 供 ①壳注册 ②网关校验 ③前端展示 三处共用）；
- 启动校验：工具 id 全局唯一、被声明的域必须存在、risk 缺省推断（方法名前缀 del/delete/update/save/submit 启发式 + 人工复核）；
- 查询：`Registry.meta(id)` / `byDomain(domain)`。

### 7.3 统一执行网关 ToolGateway

```java
Object execute(String toolId, Map<String,Object> args, RuntimeContext ctx) {
  meta = registry.meta(toolId);                       // 不存在 → DENY(幻觉工具)
  ToolSwitch.assertEnabled(toolId, ctx);              // 运行时开关
  PolicyEngine.decide(ACTION_CALL_TOOL, meta, ctx);   // deny-ask-allow（§8）
  validate(args, meta.schema());                      // 集中参数校验
  ToolSupport.enter(ctx);                             // 用户身份桥接（现状保留）
  result = meta.executor().invoke(args);              // Feign → 业务微服务（下游业务鉴权兜底）
  TaskSystem.recordAction(...); Trace.record(...);
}
```

### 7.4 readOnly/写 强制分流（Q6 + 防滥用）

- `readOnly=true`：只读；**永远 ALLOW（过审计）**；
- 写工具必须声明 `risk`：`normal`（本人数据、低副作用）默认 ALLOW+Trace；`high`（写他人/不可逆/对外触达）
  走动作级 ask（§8.5）；
- **防滥用边界（决策定稿 2026-09）**：
  - 作业/练习（`eval.submitExercise` 等自报工具）= 学生本人练习数据，**业务上允许重做改答案刷当题分**，
    → 保持 `risk=normal`，不引入 ask/deny（避免打断练题体验）；
  - 积分/画像滥用（反复提交刷积分）→ **由 lms-learning 服务端防滥用兜底**：频率/日上限/积分封顶
    （本次迭代随 P1 一起做，见 §10）；
  - **真实考试结果不存在 Agent 写路径**：考试 = 专用考试页 + 无 agent 纯答案提交 + Kafka 异步幂等链路
    （独立业务轨，见 §8.7）；Agent 面不提供"考试结果写入"工具，从源头杜绝"agent 改成绩"；
  - 管道内写回流（assess 判分 → 学习数据中心）→ Java 代码直写，LLM 无自报工具（§8.5 pipelineToken）。

### 7.5 运行时开关 ToolSwitch

- 配置静态：`lms.ai.harness-v2.tool-switch`（工具级 enable、域级 enable、灰度比例）；
- Redis 动态键 `tool:switch:{toolId}`（运维关停坏工具/灰度，不重启）；
- 判定顺序：静态关 → 动态关 → 灰度采样不中 → DENY（返回"工具暂时不可用"，LLM 重规划）。

### 7.6 Schema 强校验（Zod 的 Java 等价）

集中生成 + 校验（必填/类型/枚举/数值范围/JSON 结构），复刻 `QuestionChecker`（判分明细确定性兜底）的定位：
**LLM 输出不信任，网关层用确定性 schema 卡参数**；校验失败 → 明确错误文本回喂 LLM 重填参数，不执行。

---

## 8. L4 安全硬网关（清单 Q2/Q3/Q7）

### 8.1 PolicyEngine：统一判定入口

```java
enum ActionType { INVITE_AGENT, CALL_TOOL, SCHEDULE_TASK }

PolicyDecision decide(ActionType type,
                      Subject subject,        // userId, userType, inviterAgent
                      Object target,          // targetAgent | toolMeta
                      SessionCtx session)     // sessionId, intent, depth
```

决策结果 `ALLOW / DENY(verdict, reason) / ASK(hitlRequestId)`，全动作类型共用一套语义。
现有 `InviteAttempt.Decision` 并入 PolicyDecision（保留旧枚举做 Trace 兼容）。

### 8.2 判定链（确定性顺序，任一不过即止）

```
1 总开关(harness-v2.enabled) 关 → ALLOW(仅审计)            // 阶段回退
2 主体存在性：userId 缺失 → 高危动作 DENY(fail-closed)
3 身份角色矩阵（用户级）：ActionType×userType 预授权表
     例：CALL_TOOL(exam.*写) & userType=1 → DENY（学生不可写考试题）
        INVITE_AGENT(exam-agent) & userType=1 → DENY（学生无考试生成面）
4 目标存在性：目标 agent/工具不在注册表 → DENY(幻觉)
5 会话过滤：目标 ∈ 意图面∩声明域∩deny 名单外（§6.4）
6 深度：invite depth ≥ max → DENY
7 风险分级：
     readOnly → ALLOW
     risk=normal 写 → ALLOW(写计数入 Budget)
     risk=high（agent 邀请或写工具）→ HITL ask/deny/log（策略同现状三态）
8 ALLOW → 执行；执行方回写 PolicyDecision 供 GuardedAgentTool/ToolGateway 统一处理
```

### 8.3 身份权限结论（评审 Q3）

**权限判定在用户级（身份），能力面在会话级（过滤），双层，不在 agent 级：**

- 身份：userId/userType 从 RuntimeContext 透传整条邀请链（ToolSupport.enter → Feign 头 → 下游 UserContext），
  **业务服务做最终鉴权（保留）** —— lms-exam `QuestionServiceImpl.assertTeacher` 是样板；
- 能力：哪个 agent 能调什么 = 声明域 + 会话意图过滤 + 网关，**与调用者身份无关、与"谁的外壳"无关**；
- 因此 **agent 级权限不存在**：student 邀 teacher-agent 时执行身份仍是学生（不会升级），只是拿到 teacher 的能力面
  （im/task 域），该面受意图过滤与动作级 ask 约束。

**"学生会改自己成绩吗"——设计结论**：
- 考试写面（exam.*）学生不可达（角色矩阵第 3 步直接 DENY）+ 下游 `assertTeacher` 兜底 → **改不了考试库**；
- 残余风险只剩**自报型工具**（eval.submitExercise/submitAssessment 刷自己的做题/积分数据）→ 本设计动作级修补：
  标 `risk=high` → 会话级 ask；或在管道内部调用时带 `pipeline=true` 豁免标记（由 LearningPipelineService
  代码产生，不来自 LLM 参数）→ 服务端仍做防重/频率/上限兜底（lms-learning 侧加固，见 §12 待办）。

### 8.5 HITL 动作级（评审 Q2 补洞）

现状：`HitlService` 请求键 `agent:hitl:req:{requestId}`、批复缓存 `agent:hitl:ok|no:{sessionId}:{target}` ——
**只覆盖"高危 agent 邀请"**。升级：

```
请求体追加：actionType(INVITE_AGENT|CALL_TOOL|SCHEDULE_TASK) + actionId(targetAgent|toolId) + 动作描述
批复缓存键：(sessionId, actionType, actionId)    → agent:hitl2:ok:{sessionId}:{actionType}:{actionId}
例：invite exam-agent       → agent:hitl2:ok:12:INVITE_AGENT:exam-agent
    teacher 直推 IM        → agent:hitl2:ok:12:CALL_TOOL:im.pushMessage     ← 现状漏洞，本轮补上
    学生每轮 submitExercise → agent:hitl2:ok:12:CALL_TOOL:eval.submitExercise（ask 策略下）
```

- 豁免：管道内写回流（assess-agent 判分 → Java 直写，本就是代码路径不经 LLM 工具）；
  管道内写回流若需经工具（如 `eval.submitExercise`），带**服务端签发的 `pipelineToken`**
  （LearningPipelineService 签发，绑定 sessionId+userId+管道实例，短 TTL）→ 策略层见 token 即 ALLOW（身份仍限本人）；
- 确定性不变：放行必须有 Redis 人工批复记录，LLM 无法绕过；
- `HarnessController` 扩展：请求列表按 (sessionId) 查、前端展示动作类型与目标；
- 接口兼容：`/agent/harness/hitl/{requestId}/approve|reject|detail` 路径不变，请求体兼容旧字段。

### 8.7 考试防作弊（独立业务轨，不在 Harness 执行面）

> 业务设计定稿：**考试不经过 Agent，防作弊由"考试专用页 + 无 agent 提交链"保证，Harness 不背这口锅。**

1. **考试专用页**（lms-web）：进入考试锁定页面；离开考试页/其他站内页/外网 → 警告；
   警告累计 3 次 或 累计离开时长 > 30s → 关闭考试直接 0 分；
2. **作业/练习**：不锁、允许改答案重做（把当题分刷满是业务允许的）；
3. **提交链路（考试）**：页面无 agent，纯答案表单提交 → 异步进 Kafka
   （`lms-exam-submission` topic）→ 消费端幂等落库（答案消息带 submissionId，DB 唯一键去重，保证不丢不重）；
4. **对 Harness 的含义**：Agent 面（含 eval 管道）**不提供任何"考试结果/成绩写入"工具**；
   `submitExercise` 仅服务练习/作业数据回流，配合 lms-learning 侧频率/积分封顶。

### 8.6 沙箱

无文件/命令工具面（§7.1）→ **不实现**文件访问沙箱与命令沙箱。
`PolicyExtension` SPI 预留：未来接 File/MCP 执行器时注入 permission 检查（路径前缀白名单等），L4 判定链插入点已留。

### 8.8 Trace/Audit 动作级

`HarnessTraceService` 事件扩展：`intent_route`、`tool_allow/deny/ask`、`hitl_create/approve/reject`
（actionType+actionId 进字段）；查询接口保留按会话，追加按动作过滤。

### 8.9 学习域三分（作业 / AI 自测 / 考试）与题库边界（v1 收尾领域决策 2026-09）

**三分矩阵**（决定"谁可以碰题库、内容谁控制、风险谁担"）：

| 场景 | 出题/组卷来源 | 是否触题库 | 内容控制 | 风险责任 |
|---|---|---|---|---|
| **作业** homework | 老师走**出卷流程**（与考试卷同构：组卷→确认→发布） | 是（仅老师面） | 老师 | **泄题/漏题归老师管理** |
| **AI 自测** self-test | **仅知识库（KB/RAG）** 生成+拼接（可切题、组合 JSON 表单供前端渲染） | **否——红线** | 系统按知识点 | 禁止触碰题库，防透题 |
| **考试** exam | 老师排期发布卷子（可多课程，用户侧**排期/日历展示页**） | 是（仅老师面） | 老师 | 专用锁页 + Kafka 幂等提交防作弊（§8.7） |

**透题红线（确定性规则，不依赖 prompt）**：
- R1：**学生身份（userType=1）的任意流程（含 AI 自测管道节点）不得调用 exam.\***（含只读
  `queryQuestionPage` / `getQuestion`）——考试题库对学生不可见不可检索；
- R2：exam.* 只允许老师身份（userType=2）在其**出卷/作业发布/考试排期**流程触达；
- R3：AI 自测题目 id/来源与题库隔离（KB 生成题带来源标记，标准答案随题目存会话/任务板 artifact，
  **不落题库**），前端用组合 JSON 表单渲染；
- 落地顺序：先完成 AI 自测节点 KB 化（P2b-①），再把角色矩阵从"exam 写 4 条"收紧为"exam.*:2"（P2b-②），
  避免先收紧打挂现行自测管道。

**现状缺口 → 已修复（2026-09 代码级）**：
- ✅ `agents/exercise-agent.yaml` 已去 `exam.queryQuestionPage`，改为 `kb.ragChatCourse` 知识库生成题目包（含参考答案，学生端剥离下发）；
- ✅ `agents/assess-agent.yaml` 已去 `exam.getQuestion`，改为按题目包自带参考答案判分（Java 直写回流）；
- ✅ 角色矩阵基础规则收紧为 `CALL_TOOL:exam.*:2`（学生身份对题库全域确定性 DENY，含只读检索/取题）。

**考试排期（独立前端/业务轨，非 Harness 执行面）**：考试排期表（标题/课程集/开始-结束/状态/发布人）+
用户"我的考试"日历展示页（按报名课程过滤，可多考试），前端 lms-web 考试日历 + 专用锁页提交（§8.7）。

---

## 9. 兼容与迁移

### 9.1 现有轻量 Harness 代码去向

| 现有类 | 去向 |
|---|---|
| `GuardedAgentTool` | 保留，`callAsync` 改调 PolicyEngine（行为兼容：旧配置 `lms.ai.harness.*` 仍生效当 v2 关） |
| `DefaultAgentInviteGuard` / `AgentInviteGuard` | 逻辑并入 PolicyEngine 规则 3/5/6/7；类保留为策略链组件（可单测） |
| `HarnessSessionGuard` | 升级为 BudgetService（§5.2），保留旧方法委托以兼容调用方 |
| `HitlService` | 键模型升级动作级（§8.5），旧键只读兼容（TTL 自然过期） |
| `HarnessController` | 保留 + 新增动作级查询；路径不变 |
| `HarnessTraceService` / `HarnessKeys` | 扩展事件与键前缀（`agent:hitl2:*`、`agent:usage2:*`） |
| `HarnessProperties` | 保留（v1 回退配置），新增 `lms.ai.harness-v2.*` 配置段 |

### 9.2 yaml/声明改动

- 17 个 `agents/*.yaml`：`tools` 字段语义 → 能力域引用（§6.1），`risk`/`subAgents`/prompt 不动；
- 新增 `capabilities` 域常量表（im/task/learning/exam/course/kb/media/search/remark/user/statistics/pipeline/eval…），
  与 ToolRegistry domain 对齐；
- `AgentDeclaration` 增加 `capabilities` 解析，工具元数据从注册表取。

### 9.3 配置段（application.yml）

```yaml
lms:
  ai:
    harness-v2:
      enabled: true                       # 总开关（false=全回退 v1 行为）
      intent:
        enabled: true                     # L0（false=纯自由 ReAct）
        llm-model: qwen-turbo
        min-confidence: 0.6
        quick-entry: true                 # 确定性快捷入口直通
      orchestrator:                       # L1
        compress-messages-at: 40
        compress-model: qwen-turbo
        budget-policy: terminate          # terminate|compress|direct-only
      scheduling:
        shell-mode: true                  # L2 网关壳（false=现状直注 Bean，灰度回退）
      tool-gateway:
        enabled: true
        validate-schema: true
      tool-switch: {}                     # L3 静态开关
      policy:                             # L4
        high-risk-policy: ask             # 复用 v1 三态
        student-write-exam: deny          # 角色矩阵可配
        self-report-risk: high            # submitExercise/submitAssessment 定级
        hitl-ttl-seconds: 600
        approval-ttl-seconds: 1800
```

### 9.4 Redis 键汇总

```
agent:usage2:{sessionId}                L1 预算（turns/tokens/writeCalls/invites）
agent:session:{sessionId}:summary       L1 压缩摘要
agent:hitl2:req:{requestId}             L4 动作级确认请求
agent:hitl2:ok|no:{sessionId}:{actionType}:{actionId}   L4 批复缓存
tool:switch:{toolId}                    L3 动态开关
agent:trace2:{sessionId}                动作级 Trace（可复用 v1 键+新事件）
```

---

## 10. 阶段路线图（每阶段可独立交付/回退/验证）

| 阶段 | 内容 | 交付物 | 验证点 | 回退 |
|---|---|---|---|---|
| P0 | L4 先行：动作级 HITL + 角色矩阵 + PolicyEngine（v1 决策迁入，动作类型 INVITE/CALL_TOOL/SCHEDULE） | `harness2/`（policy）包、PolicyEngine、HitlService 动作级升级 | 高危动作（含 teacher 直调 im.pushMessage）触发动作级 ask；学生触达 exam 写面被角色矩阵 DENY | `harness-v2.enabled=false` |
| P1 | L3 + 防滥用：ToolRegistry + 集中 Schema + ToolSwitch + readOnly 分流 + **pipelineToken** + **lms-learning 侧频率/积分封顶（本次迭代）** | `tools/registry`、`tools/gateway`、lms-learning 封顶 | 管道 token 豁免；练习刷分上限生效；坏工具动态关停 | `tool-gateway.enabled=false` |
| P2 | L0：IntentRouter（两级）+ 意图预收窄 + Intent Trace（白名单收紧**暂缓**，只做提示+收窄） | `intent/` 包、枚举表、统计接口 | 快捷入口直通命中；兜底率指标可查 | `intent.enabled=false` |
| P3 | L2：网关壳化（ToolFactory 改造），agent 不再持有业务实现 | `tools/shell` | LLM 视角工具不变；壳全部过网关（网关日志 100% 覆盖） | `scheduling.shell-mode=false` |
| P4 | L1：TurnLoopEngine 接管 + BudgetService + ContextCompressor(LLM 摘要) | `engine/` 包 | 预算多维度生效；40 轮后触发摘要压缩 | `budget-policy`/阈值回退 |
| P5 | L1 TaskSystem：agent_task 扩展 + 任务树 + 管道任务板化 + QueryEngine（**tool 级只落写工具+管道节点，读工具走 Trace**） | `task/` 扩展、DDL、/agent/tasks 前端 | 诊断→规划→习题→测评 全程任务树可见、可续跑 | 旧 TaskService 路径保留 |

P0–P5 顺序依赖弱，P0/P1 可先行合入主干（补真实漏洞），P2 可与 P3 并行。

**P2b（学习域 rails，v1 收尾，与 P2 并行推进）**：
- ① AI 自测 KB 化：exercise/assess 节点去题库化（KB 生成题 + 标准答案进管道 artifact，§8.9 R3）；
- ② 收紧角色矩阵：KB 化验证后把 CALL_TOOL exam 基础规则从"4 个写方法"扩为 `exam.*:2`（§8.9 R1/R2）；
- ③ 作业：老师出卷流程发布（复用卷面生成 + 发布状态，作业表/作业提交归属学生）；
- ④ 考试排期表 + 用户考试日历接口 + 前端日历页/专用锁页 + Kafka 提交消费端幂等（§8.7/§8.9）。
- 验收：AI 自测全链路零 exam.* 调用（Trace 可证）；学生经任何路径触 exam.* 被 DENY；作业/考试卷面无学生直触。

考试专用页 + Kafka 幂等提交链路 = 独立业务轨（lms-web/lms-exam），不占 P0–P5 排期。

---

## 11. 验收清单（状态截至 2026-09；[x]=代码级已落地，[ ]=运行期 E2E 待联调/前端轨）

- [x] P0：PolicyEngine 动作级 HITL + 角色矩阵已落地（代码级）；运行期 approve/deny 流程待联调验证
- [x] P0：学生触达 exam.* 被角色矩阵确定性 DENY（GuardedFunctionTool 网关 + `CALL_TOOL:exam.*:2`）
- [x] P1：`pipelineToken`（PipelineTokenService：签发/一次性校验，绑 sessionId+userId）
- [x] P1：lms-learning 做题 200/天、测评 30/天 日上限（EvalDataServiceImpl）
- [x] P1：工具运行时开关（静态名单 + Redis `tool:switch:{toolId}` 动态关停，ToolControlService）
- [x] P2：意图路由两级（规则 + qwen-turbo LLM 分类，intent-llm-enabled 开关）+ ctx + Trace
- [x] P3：网关壳化（GuardedFunctionTool 同名壳，100% 工具调用过 PolicyEngine；专家代码无业务实现）
- [x] P4：预算多维度（turns/tokens/writes/invites 计数上限）+ LLM 摘要压缩（ContextCompressor，summary-enabled）
- [x] P5：会话级任务板（turn/邀请/写工具/管道节点落板 + GET /agent/tasks/tree 任务树）
- [x] 学习域 rails 服务端：出卷骨干（exam_paper 快照+服务端确定性判分）、发布物排期（作业/考试+窗口交卷）、作答记录回流 learning（source 3/4）
- [ ] 运行期 E2E（需 Redis/Nacos/LLM key/联调）：HITL approve 放行、动态关停工具、意图命中率、压缩后 QA 回归
- [ ] 前端轨（lms-web，对接契约见 §13）：考试日历页、作业/考试答题页、考试专用锁页、Kafka 幂等提交消费端
- [x] 回归代码级：/agent/chat、IM 回流、ask-teacher、qa_remind 调用路径未被改动破坏（默认开关下行为不变）；全量 mvn compile EXIT=0

---

## 12. 决策定稿（已确认 2026-09）

| # | 决策点 | 结论 | 影响 |
|---|---|---|---|
| D12-1 | lms-learning 侧防滥用（频率/积分封顶）排期 | **本次迭代随 P1** | P1 交付物含 lms-learning 服务端封顶（§10） |
| D12-2 | 管道豁免形态 | **服务端签发 `pipelineToken`**（绑定 sessionId+userId+管道实例，短 TTL，一次性） | §8.5；P1 已实现 PipelineTokenService |
| D12-3 | 学生自报类工具策略 | **不做 ask/deny 灰度**——作业/练习允许重做刷分（业务设计放开）；真实考试 = 专用锁页 + 无 agent + Kafka 异步幂等提交（独立业务轨 §8.7）；积分账由 learning 侧封顶兜 | §7.4/§8.7 重写；lms-learning 日上限已落地 |
| D12-4 | 意图收紧到枚举白名单 | **暂缓**：L0 只做"提示 + 收窄"，保留自由 ReAct | §4/P2 范围 |
| D12-5 | 任务板写放大 | **tool 级只落写工具 + 管道节点**，读工具走 Trace | §5.3/P5 |

**P1 落地备注（2026-09）**：
- 工具执行网关 = `GuardedFunctionTool`（Toolkit 内同名替换，schema 不变，执行前过 ToolControlService + PolicyEngine）；回退开关 `harness-v2.tool-gateway-enabled=false`；
- readOnly 取 @Tool 注解，risk 默认 normal + `tool-risk` 覆盖表；**默认无 risk=high 工具** → 动作级 ask 当前仅对 invite 高危专家生效；
- `im.pushMessage`（teacher 直推/学生问老师流共用同一工具）是否动作级确认存在**流归属歧义**：ask-teacher 流中学生身份触发推送给老师属预期行为，若全局标 high 会误拦——该场景待 P2 引入"流/意图上下文"后按流收窄（teacher 直推 ask、学生问老师放行），P1 不默认开启；
- lms-learning 封顶：每用户每日做题上报 ≤200 条、测评报告 ≤30 份（EvalDataServiceImpl 常量，代码级）

---

## 13. 前端对接契约（lms-web，作业/考试/AI 自测 rails 交付面）

> 服务端 rails 已闭环（接口真实可用），lms-web 按下述契约实现页面；考试专用锁页 + Kafka 提交为前端轨后续里程碑。

### 13.1 页面与流程

| 页面 | 角色 | 流程/要点 |
|---|---|---|
| 考试日历/我的考试·作业 | 学生 | `GET /exam-schedules/mine?courseIds=1,2&bizType=1\|2`（课程 ids 传用户已报名集合）→ 卡片/日历渲染；作业/考试分类型展示 |
| 卷面答题页 | 学生 | `GET /exam-papers/{id}`（**已剥离答案/解析**：stem/type/options 组合 JSON 表单渲染）→ 作答 → `POST /exam-schedules/{id}/submit` |
| 作业重做 | 学生 | 截止前可重复 submit（服务端确定性判分，刷当题分业务允许） |
| 出卷工作台 | 老师 | `POST /admin/exam-papers`（选题列表）→ `GET /admin/exam-papers/{id}`（**含答案预览**）→ `POST /admin/exam-papers/{id}/publish` → `POST /admin/exam-schedules`（作业 bizType=2/考试 bizType=1，挂卷+时间+课程） |
| 我的发布管理 | 老师 | `GET /admin/exam-schedules`、`GET /admin/exam-papers`；结束：`POST /admin/exam-schedules/{id}/close` |
| 考试锁页（里程碑） | 学生 | 考试页进入即锁（全屏）；离开/切页/外链警告，累计 3 次或 30s 判 0 分；**无 agent**，提交走 Kafka 异步（见 13.3） |

### 13.2 关键数据结构

- 卷面学生视图 item：`{seq, questionId, stem, type(1单选2多选3判断), options?(当前题干内嵌，预留), difficulty, score}`（无 answer/analysis）；
- 提交 body：`{"answers":[{"questionId":1,"userAnswer":"A"}]}`（多选 `"A,B"`，判断 `"true"/"false"`）；
- 判分返回：`{paperId,title,totalScore,questionCount,answeredCount,correctCount,score,details:[{questionId,seq,correct,score}], scheduleId,bizType}`；
- 排期字段：`{id,title,description,bizType,paperId,courseIds(逗号),teacherId,startTime,endTime,durationMinutes,status}`。

### 13.3 考试 Kafka 提交轨（前端轨里程碑，独立排期）

- topic：`lms-exam-submission`（消息含 submissionId 幂等键 + scheduleId + userId + answers）；
- **消费端已落地（服务端）**：`lms-exam ExamSubmissionConsumer` —— exam_submission 唯一键幂等落库（pending→done/failed，DuplicateKey 兜底）→ 卷面快照确定性判分 → AsyncUser 桥学生身份回流 lms-learning（source=4）；
- 前端/客户端责任：考试页锁页计时（离页 3 次或累计 30s 判 0 分）→ 生成 submissionId → 发 topic（至少一次投递语义由消费端幂等兜底）；
- 注意：考试提交轨上线后，**考试作答改走 Kafka 异步**，同步 `POST /exam-schedules/{id}/submit` 保留给作业（作业允许即改即判重做）；避免双通道重复回流；
- 考试页全程不出现 agent/题库检索，试卷取 `exam-papers/{id}` 已发布快照。
