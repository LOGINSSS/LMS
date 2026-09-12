# 全局 Harness 规格（GLOBAL_HARNESS_SPEC）

> 状态：**已实现（lms-ai `com.lms.ai.harness` 包）** · 依据：`docs/AGENTSCOPE_INVITE_SUMMARY.md` 方向 A（轻量最小改造，会话全局一份，不下沉专家 Agent）
> 关联代码：`lms-ai/src/main/java/com/lms/ai/harness/*`、`AgentDeclaration.risk`、`AgentRegistry` 静态校验、`ToolFactory` 邀请工具包装、`AgentChatService` 生命周期接入
>
> 一句话：**不推翻 `invite_agent`（SubAgentTool）邀请制原语，在其外围加一层会话全局的确定性管控护栏；专家 Agent 保持薄业务外壳，不感知 Harness。**

---

## 0. 背景（为什么需要 Harness）

裸用 AgentScope `SubAgentTool` 邀请原语时存在 6 类工程风险（详见 `AGENTSCOPE_INVITE_SUMMARY.md` §2），核心是：
LLM 主 Agent 拥有无约束邀请权，全靠 prompt 软约束；高危写操作专家被幻觉邀请会直接触发业务写库。

**安全底线认知**：安全靠 Host 确定性代码（本规格实现），prompt 仅降低概率、不作安全屏障；
Loop-Engineering、Plan-Mode 是上层可选组件，本规格不实现（预留策略扩展点）。

---

## 1. 架构定位

```
用户 → AgentChatService（会话生命周期：turn/token 预算）
        └─ 主 Agent（teacher/student）ReAct 循环
              └─ 想邀请专家 → 调用 invoke_exam_agent（SubAgentTool 形态）
                    └─【Harness 拦截层】GuardedAgentTool（onBeforeAgentInvite）
                          ├─ 深度校验          —— 防死循环/无限嵌套
                          ├─ 会话白名单        —— 幻觉邀请/越权邀请 → 拒绝，主 Agent 重规划
                          ├─ 高危校验(risk=high) → 强制 HITL 人工确认（ask/deny/log 策略）
                          └─ ALLOW → 委托原生 SubAgentTool 执行（专家无感知）
  专家 Agent（exam/course/...）：薄业务外壳，不含 Harness 逻辑（业务鉴权/入参校验仍在工具层兜底）
```

- **管控收敛在会话全局**：所有状态（HITL 请求、批复缓存、Trace、用量）按 `sessionId` 收敛在 Redis，随会话 TTL 清理。
- **专家 Agent 不感知**：拦截在 `GuardedAgentTool`（工具工厂统一包装），专家内部无任何 Harness 代码。
- **两阶段隔离语义**：高危操作被拦截后主 Agent 结束本轮告知用户 → 用户经 `/agent/harness/hitl/{id}/approve` 批准 → 用户重试 → 批复缓存命中 → 放行。硬编码 HITL，不依赖 Agent 内部 prompt 询问。

---

## 2. 配置（application.yml `lms.ai.harness`）

| 键 | 默认 | 说明 |
|---|---|---|
| `lms.ai.harness.enabled` | true | 总开关；false = 全部放行只留日志（阶段回退） |
| `lms.ai.harness.invite.max-depth` | 3 | 邀请链最大深度（含首层），超限拒绝 |
| `lms.ai.harness.invite.high-risk-policy` | ask | 高危专家邀请策略：`ask`(强制 HITL)/`deny`(直接拒绝)/`log`(记录放行，联调用) |
| `lms.ai.harness.invite.deny-agents` | [] | 会话级额外禁止邀请名单（叠加声明白名单之上） |
| `lms.ai.harness.invite.whitelist-override` | {} | 会话白名单覆盖：inviter → 允许邀请集合（可选收窄） |
| `lms.ai.harness.hitl.enabled` | true | HITL 通道开关；false = 高危退化为直接拒绝（fail-closed） |
| `lms.ai.harness.hitl.request-ttl-seconds` | 600 | 确认请求有效期，超时自动失效 |
| `lms.ai.harness.hitl.approval-ttl-seconds` | 1800 | 批复缓存：批准后同会话同目标窗口内免重复确认 |
| `lms.ai.harness.hitl.hint` | 说明 | 确认请求展示给用户的默认提示 |
| `lms.ai.harness.session.max-user-turns` | 0 | 会话最大用户轮次（0=不限），超限抛异常提示新建会话 |
| `lms.ai.harness.session.max-tokens` | 0 | 会话最大估算 token（0=不限；近似 text.length()/2） |
| `lms.ai.harness.trace.enabled` | true | Trace 埋点开关 |
| `lms.ai.harness.trace.recent-limit` | 200 | 每会话 Redis 环形缓冲保留条数 |
| `lms.ai.harness.trace.ttl-days` | 7 | Trace Redis TTL |

> 高危声明在 agent yaml：`risk: high`（exam/course/media/im 已标注）。判据：写业务库 / 不可逆删除 / 对外触达。

---

## 3. 声明与注册（静态安全底线）

### 3.1 AgentDeclaration 新增字段

```java
private String risk = RISK_NORMAL;   // high | normal；isHighRisk() 供拦截决策
```

### 3.2 AgentRegistry 静态校验（启动即失败，不留运行期隐患）

1. **role=sub 的专家不得声明 subAgents** —— 专家是纯被动 Worker，不会主动邀请其他 Agent；
2. **声明的 subAgents 必须存在** —— 防 typo 静默丢失邀请能力；
3. **邀请图无环** —— DFS 三色标记检测，防死循环互邀（运行时深度拦截是第二道防线）。

---

## 4. 核心模块（`com.lms.ai.harness`）

### 4.1 拦截层：`GuardedAgentTool`（implements AgentTool，包装 SubAgentTool）

挂载点：`ToolFactory.subAgentTool(name)` 统一返回 Guarded 包装，`Toolkit.registerAgentTool` 注册。
即 AgentScope 中"父 agent 邀请子 agent"唯一入口被接管——任何一次真实邀请必经。

`callAsync(ToolCallParam)` 流程：
1. 从 `ToolCallParam.getRuntimeContext()` 取 sessionId/userId/userType；`getAgent().getName()` 取邀请者；
2. 组装 `InviteAttempt` → `AgentInviteGuard.check()` 决策；
3. 记录 Trace（谁邀请谁、决策、原因、HITL 请求 id）；
4. **ALLOW** → 深度 +1 写入 RuntimeContext（子链可见），委托原生 `SubAgentTool.callAsync`，完成后恢复深度；
5. **DENY / HITL_REQUIRED** → **不执行任何子 agent**，构造文本工具结果回喂 LLM
   （"Harness 拦截：…请重新规划 / 需要人工确认 requestId=…"），让主 Agent 重规划或转述用户。

### 4.2 决策链：`AgentInviteGuard` / `DefaultAgentInviteGuard`

确定性代码顺序（任一不过即拒绝，不依赖 prompt）：
1. 总开关关闭 → 放行；
2. **注册表存在性**：目标不在注册表（幻觉邀请）→ DENY_WHITELIST；
3. **会话白名单**：命中 `deny-agents` → DENY；配置 `whitelist-override` 则按其收窄；
   否则 target 必须 ∈ inviter 声明 `subAgents`；sub 专家（无 subAgents 声明）天然无法外邀 → DENY；
4. **深度**：`depth >= max-depth` → DENY_DEPTH（防死循环）；
5. **高危**：`risk=high` → 按策略：
   - `deny` → DENY_HIGH_RISK（直接不可用，主 Agent 重规划）；
   - `log` → 放行并告警（联调）；
   - `ask`（默认）→ 无会话上下文 fail-closed 拒绝；有批准缓存 → 放行；有拒绝缓存 → 拒绝；
     否则 `HitlService.create` 生成确认请求 → HITL_REQUIRED（requestId 回喂 LLM）。

### 4.3 HITL：`HitlService`

- 请求：`agent:hitl:req:{requestId}`（hash：sessionId/userId/inviter/target/reason/status/createTime），TTL 超时失效；
- 批复：`approve` 写批准缓存 `agent:hitl:ok:{sessionId}:{target}`；`reject` 写拒绝缓存 `agent:hitl:no:{...}`（TTL=approval-ttl）；
- 判定：`isApproved/isRejected` 供守卫查询 —— 同会话同目标窗口内免重复确认；
- **安全性**：确认请求由确定性代码生成，放行必须有 Redis 中的人工批复记录，LLM 无法自行绕过。

### 4.4 Trace：`HarnessTraceService`

- 事件：`invite`（邀请决策）、`hitl_create/approve/reject`、`session_limit/terminated`；
- 每会话 Redis 环形缓冲（`agent:trace:{sessionId}`，保留 recent-limit 条，TTL ttl-days）+ 结构化日志；
- 查询：`GET /agent/harness/trace?sessionId=&limit=`（仅会话归属人）。

### 4.5 会话生命周期：`HarnessSessionGuard`

- Redis hash `agent:usage:{sessionId}`：`turns`（每轮用户消息 +1）/ `tokens`（估算 length/2）；
- 每轮对话前 `checkBeforeTurn`：超限抛 `CommonException` 提示新建会话；
- 结束会话时 `clear`（AgentSessionService.closeSession 接入）。

---

## 5. 接口（REST，网关 `/agent/**`）

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/agent/harness/hitl/{requestId}/approve` | 人工批准高危确认请求（归属人或老师） |
| POST | `/agent/harness/hitl/{requestId}/reject` | 人工拒绝 |
| GET | `/agent/harness/hitl/{requestId}` | 确认请求详情（前端展示） |
| GET | `/agent/harness/trace?sessionId=&limit=` | 会话 Trace（归属人） |
| GET | `/agent/harness/usage?sessionId=` | 会话用量（归属人） |

---

## 6. 典型时序：老师让 exam-agent 出题（高危 HITL）

```
老师: "给《数据结构》出一套期中试卷"
teacher-agent 想邀请 exam-agent（risk=high）
  → GuardedAgentTool 拦截: 白名单 ✓(teacher 声明含 exam-agent) / 深度 0<3 ✓
  → 高危 ask 策略: 无批复 → HitlService.create → HITL_REQUIRED(requestId=h-xxx)
  → 工具结果回喂 LLM: "需要人工确认 requestId=h-xxx，结束本轮告知用户"
  → 老师看到: "生成试卷需要您确认（请求 h-xxx），确认后我再继续"
老师: POST /agent/harness/hitl/h-xxx/approve   （或前端按钮）
老师: "继续出题"
teacher-agent 再次邀请 exam-agent
  → GuardedAgentTool: 批准缓存命中(ok:sessionId:exam-agent) → ALLOW → exam-agent 执行出题
```

**若主 Agent 幻觉邀请非白名单专家**（如 student-agent 幻觉邀请 course-agent）：
拦截器 DENY_WHITELIST → 工具结果告知"不在邀请白名单，重新规划" → 主 Agent 自我纠正，
不会产生任何真实业务调用 —— 幻觉被确定性代码拦截，prompt 不承担安全责任。

---

## 7. 与现有代码的接线点（改动清单）

| 文件 | 改动 |
|---|---|
| `declaration/AgentDeclaration.java` | +`risk` 字段、`isHighRisk()`、常量 |
| `registry/AgentRegistry.java` | +启动静态校验（sub 不声明 subAgents / 子 agent 存在 / 邀请图无环） |
| `tools/ToolFactory.java` | 注入 guard+trace；`subAgentTool()` 返回 `GuardedAgentTool` |
| `chat/AgentChatService.java` | 每轮会话生命周期检查 + 用量记录 |
| `session/AgentSessionService.java` | closeSession 清理 Harness 用量 |
| `factory/PersonalAgentFactory.java` | system prompt 提示 Harness 行为（仅降概率，非安全屏障） |
| `controller/HarnessController.java` | 新增（HITL 批复 / Trace / usage） |
| `config/HarnessProperties.java` | 新增配置类 |
| `resources/agents/*.yaml` | exam/course/media/im-agent 标注 `risk: high` |
| `resources/application.yml` | +`lms.ai.harness.*` 配置段 |

## 8. 演进预留（本规格未实现，策略点已留）

- **Plan-Mode（规划-执行两阶段）**：可在 `AgentInviteGuard` 前加"规划态"拦截（规划阶段所有邀请转 DENY+提示），
  需会话级状态机支持，属 LLM 侧约束，不能替代本层硬拦截；
- **Loop-Engineering（外层校验-重试闭环）**：适用于试卷生成等需产出物校验的业务，放在 `AgentChatService`/编排层，
  复用本层防护（Loop 不修复单次安全，依赖本层已拦截）；
- **工具级白名单裁剪**：`whitelist-override` 已支持"某 inviter 允许邀请哪些专家"，
  更细的工具级裁剪可在专家声明 tools 上扩展（当前由 yaml tools 决定）。

## 9. 验收检查单

- [ ] 启动：AgentRegistry 静态校验通过（sub 无 subAgents、无环）；`risk: high` 声明加载成功
- [ ] 拦截：teacher 会话邀请 exam-agent 在 `high-risk-policy=ask` 下返回"需人工确认"，无真实业务调用
- [ ] 幻觉：student 会话 LLM 幻觉邀请非白名单专家 → DENY，Trace 有记录，主 Agent 能重规划
- [ ] HITL：approve 后同会话重试放行；reject 后窗口内直接拒绝；请求超时自动失效
- [ ] 深度：超 max-depth 拒绝（配置 max-depth 调小可快速验证）
- [ ] 生命周期：max-user-turns=3 时第 4 轮提示新建会话
- [ ] Trace：`/agent/harness/trace` 返回按会话的邀请决策链；越权读他人会话被拒
- [ ] 专家薄壳：exam/course 等专家代码无 Harness 依赖，行为不受影响
