# LMS AI 模块整体探查与 Harness 完整度评估

评估日期：2026-09-11

## 结论

`lms-ai` 已经具备可运行的多 Agent 与 Harness 核心骨架，但当前源码仍有历史功能包，尚未完成能力地图中的全量物理迁移。当前已经完成两条可验证的结构切片：Application 通过 LMS 自有 `AgentRuntime` 调用 Agent；`ContextAssembler` 通过 `LongTermMemoryPort` 统一装配 Session 历史、摘要、当前消息与长期记忆候选。

本轮已收口三项 P0：工具未知元数据与包装失败 fail-closed、YAML 工具能力精确到方法、Task API/工具的身份与 owner 授权，并补齐 `grab` 工具域。Harness 的角色绑定、邀请白名单、深度限制、工具策略、高风险 HITL、Trace 与任务树均有真实代码；但默认预算仍为不限，动态工具开关在 Redis 故障时仍按可用性优先放行，SSE 不是模型原生流式且 HITL 事件依赖文本正则。当前结论是“核心治理链与 P0 授权边界可用，运行时预算、事件和记忆治理仍未达到完整生产级”。

## 本次结构切片

已落地边界：

```text
chat/AgentChatService
    -> application/context/ContextAssembler
        -> session/AgentSessionService
        -> application/port/out/LongTermMemoryPort
            <- infrastructure/memory/ProfileMemoryAdapter
    -> application/port/out/AgentRuntime
        <- infrastructure/ai/agentscope/AgentScopeRuntimeAdapter
            -> PersonalAgentFactory -> AgentScope / DashScope
```

保持不变：HTTP/SSE 路径、JWT 角色绑定、Session Redis key、MySQL 表、意图路由、画像注入、Trace、任务落板、错误消息以及底层阻塞式模型调用。

后续仍需迁移：`chat` 到 application conversation、Session 持久化适配器归位、Agent 定义与运行时进一步解耦、Feign/MyBatis/Redis 适配器归位，以及学习固定管道运行时端口化。旧 AgentScope ReMe 兼容层已经由当前 HTTP Job 适配器替换。

## 当前能力盘点

| 层级 | 当前实现 | 状态 | 事实边界 |
|---|---|---|---|
| 入口层 | `AgentController`、`LearningController`、`TaskController`、`HarnessController`、`ImCallbackController` | 部分完成 | Agent/Session/Profile/HITL 已有入口；旧 `/ai/**` 演示入口仍并存 |
| 应用层 | `AgentChatService`、`OrchestratorService`、`LearningPipelineService` | 部分完成 | Chat 主链已依赖运行时端口；固定学习管道仍直接依赖 AgentScope/Mapper/Feign |
| 路由层 | 规则路由 + 可选 LLM 分类 | 规则层启用 | `intent-llm-enabled=false`，UNKNOWN 进入自由 ReAct |
| Context | `application/context/ContextAssembler`、`ContextCompressor` | 基础边界已实现 | 统一装配 Session 历史、摘要、当前消息和最多 5 条长期记忆；意图仍作为独立 Invocation 字段，统一 token 预算尚未实现 |
| Session | MySQL 会话元数据 + Redis 消息/摘要 + 归属校验 | 已实现 | 40 条触发裁剪；自动摘要默认关闭 |
| Memory | 召回/显式 Wiki/会话沉淀端口、MySQL 画像、ReMe HTTP 适配器 | 部分完成 | Top-5、写入/更正/删除、会话关闭 `auto_memory` 及可靠 Outbox 已实现；默认关闭，实际部署与授权摘要仍待实现 |
| Agent 定义 | 17 份 YAML：2 个 personal + 15 个 sub | 已实现 | 注册时校验重名、目标存在、sub 不得继续邀请、邀请图无环 |
| 编排 | personal Agent 的 agent-as-tool；学习评测固定管道 | 部分完成 | 前者模型驱动，后者 Java 确定性；并非所有编排都经过同一运行时边界 |
| 能力/工具 | 14 个已注册工具域，67 个 `@Tool` 方法 | 已实现 | Agent 只获得 YAML 精确声明的方法；缺失域/方法、同 Agent 函数重名或网关包装失败均拒绝构建 |
| 外部集成 | 业务 Feign、Python RAG、IM、Task、DashScope | 已实现/按配置启用 | 课程 RAG 的 Milvus 与个人长期记忆是两条独立链路 |
| 可观测 | Redis Trace、MySQL 任务树、SSE 生命周期事件 | 部分完成 | SSE delta 是完整答案后切片；任务节点尚未全类型浏览器验证 |

## ReMe 与个人 Wiki

当前已完成最新 ReMe HTTP Job 接入代码，但还不能描述为生产环境已启用：

- `lms.ai.agent.reme.enabled` 默认仍是 `false`，尚未配置或联调实际的按用户 workspace 路由代理。
- `ReMeMemoryAdapter` 通过 `/search` 召回 Top-5，并通过 `/write`、`/edit`、`/delete` 支持显式 Wiki 生命周期。
- Wiki 路径由 LMS 生成，启用配置强制包含 `{userId}`；当前 ReMe 搜索本身没有 workspace 参数，部署层必须提供一人一 workspace。
- 旧 `agentscope-extensions-reme`、`ReMeMemoryFactory` 和空壳 `ProfileLongTermMemory` 已删除，Application 不暴露 ReMe 类型。
- 会话关闭通过 MySQL Outbox 异步调用 `auto_memory`：关闭状态与任务同事务提交，Redis 在提交后清理，远端失败指数退避并在超限后进入死信；成功后清空消息 payload。教师只读授权摘要、死信管理入口及完整敏感信息生命周期仍未实现。

目标方案不把向量数据库设为前置条件。第一版个人 Wiki 使用文件原生存储、BM25 与 WikiLink 扩展；只有评测证明召回不足时才增加 embedding。课程知识库继续保留按课程隔离的 Milvus RAG，不能因为个人 Wiki 不用向量而删除课程 RAG。

## Harness 完整度

| 维度 | 状态 | 证据与缺口 |
|---|---|---|
| 身份与个人 Agent 绑定 | 较完整 | Gateway/Common 负责 JWT 透传；`AgentController` 把 student/teacher 与 JWT 角色绑定，4 个授权测试通过 |
| Agent 声明约束 | 较完整 | 注册表静态校验目标存在、sub 无下级、邀请图无环 |
| 邀请治理 | 较完整 | 声明白名单、覆盖白名单、拒绝名单、角色矩阵、最大深度 3、高风险策略 |
| 工具治理 | 较完整 | YAML 方法级白名单、角色规则、readOnly/risk、HITL、写预算已存在；未知元数据、缺失域/方法和包装失败均 fail-closed；动态开关 Redis 故障仍 fail-open |
| 会话预算 | 部分完成 | turn/token/write/invite 计数机制存在，但默认 0 表示不限，生产约束没有生效 |
| HITL | 可用但未闭环 | Redis 请求/批复与真实拒绝已验证；SSE 仍从自然语言正则提取 requestId，不是结构化运行时事件 |
| 资源级授权 | 较完整 | Session/Trace/tree 有归属校验；Task 查询/取消按 owner 隔离，HTTP 发布仅教师；“任意教师可批复 HITL”仍需业务确认 |
| 可观测与审计 | 部分完成 | Trace、任务树、SSE stage/meta/delta/done/error 已有；缺统一 errorId、耗时、输入输出摘要和全节点验证 |
| 失败与取消 | 不完整 | 模型失败可转 LMS 异常；SSE 客户端断开不会取消下游，线程池拒绝没有专用 SSE 语义 |
| 记忆治理 | 部分完成 | 显式写入/更正/删除、workspace 隔离、可靠 Outbox、重试/死信及投递 Trace 已落地；授权摘要、死信操作入口与敏感信息保留策略仍缺失 |

## 优先整改项

### 本轮完成的 P0 安全闭环

1. 未知工具元数据现在返回 `UNKNOWN_TOOL` 拒绝；工具域、声明方法、同 Agent 函数重名或网关包装异常会中止 Toolkit 构建，不再恢复裸工具。
2. Task HTTP 发布仅教师，查询/取消按当前用户 owner 隔离；Task Agent 工具缺少运行时身份时在进入业务层前拒绝。
3. `GrabTools` 已注册到能力域，`course-agent` 的抢课能力声明可以被正常解析。

### 仍需确认的安全策略

1. HITL 是否允许任意教师跨用户批复；若不允许，需要加入请求发起人、课程或组织范围授权。
2. 动态工具开关在 Redis 故障时是继续 fail-open，还是对高风险工具改为 fail-closed。
3. 为生产环境配置非零的 turn、token、write、invite 预算，并加入配置基线测试。

### P1 架构与运行时

1. 让 `LearningPipelineService` 也通过 LMS 自有运行时端口执行节点，移除 Application 对 AgentScope、Mapper 与 Feign 的直接依赖。
2. `ContextAssembler`、上下文压缩归位和 `LongTermMemoryPort` 已完成；下一步补统一 token 预算、记忆来源标签与授权摘要。
3. 把模型原生 delta、取消信号和结构化 HITL 事件加入运行时端口，替换回答后切片与正则提取。

### P1 ReMe 个人 Wiki

1. 当前 HTTP Job 适配、`digest/wiki/{uuid}.md` 布局、显式写入/更正/删除与 Top-5 召回已经落地；默认仍关闭。
2. 会话结束 `auto_memory` 已使用 MySQL Outbox 可靠投递，并具备租约、指数退避、死信及会话级 Trace；下一步部署并联调可信的按用户 workspace 路由。
3. 增加教师只能读取用户显式授权学习摘要的策略与测试；禁止直接跨用户读取私有 Wiki。

### P2 可观测与异步

1. 任务树补齐耗时、输入输出摘要、errorId、failed/cancelled 事件并做浏览器全节点验证。
2. 打开 Kafka 通知前补幂等、重试与消费观测；当前 `kafka-enabled=false`。
3. 为规则路由、摘要压缩和 ReMe 分别建立离线评测后再调整默认开关。

## 验证结果

- `ContextAssemblerTest`：3 个通过，验证历史/当前消息、长期记忆、失败降级与应用层强制 Top-5。
- `ProfileMemoryAdapterTest`：2 个通过，验证 MySQL 画像转换为空间无关的长期记忆片段及空画像处理。
- `AgentChatServiceTest`：1 个通过，验证 Context、意图、身份、Session 与 AgentRuntime 请求装配。
- `AgentScopeRuntimeAdapterTest`：2 个通过，验证消息/RuntimeContext 映射与成功、异常两条生命周期关闭路径。
- `AgentControllerAuthorizationTest`：4 个通过，验证学生无法使用教师入口或教师 Agent。
- Harness/Task 新增测试覆盖未知元数据、裸工具回退、YAML 精确方法白名单、缺失工具域/方法、跨领域函数重名、`grab` 域、Task 入口/服务/工具身份和 owner 隔离。
- ReMe 新增测试：9 个通过，覆盖按用户 endpoint、HTTP Job 请求体、Bearer token、Top-5、画像双向降级、服务端路径、显式写入/更正/删除及非法 ID。
- `lms-ai` 全量测试：45 个通过，0 失败，0 错误。
- `mvn ... -DskipTests package`：成功生成 `lms-ai/target/lms-ai.jar`。
- Windows 下 Surefire 默认 fork 的 manifest classpath 出现跨盘校验异常；使用 `-DforkCount=0` 后同一测试集全部通过。此问题属于测试启动环境，不是断言失败，但 CI 应使用普通 fork 再验证。

## 本次代码审查

结论：运行时边界、P0 加固、Context/Memory 拆分与 ReMe Wiki HTTP 适配可接受。Application 的 `AgentRuntime`、`LongTermMemoryPort`、`PersonalWikiPort` 均未暴露 AgentScope、ReMe、Feign、MyBatis 或 Redis 类型；ReMe 召回失败按 best-effort 回退画像，画像失败也不会丢弃 Wiki 结果，写操作则明确失败，Top-5、Bearer token 与服务端生成路径已有测试。打包产物只含新 HTTP 适配器，不含旧 ReMe 扩展。尚未完成的重点是实际 ReMe 多 workspace 部署联调、自动沉淀/记忆审计、模型原生流式/取消、结构化 HITL 事件和生产预算基线。
