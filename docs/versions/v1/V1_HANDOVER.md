# v1 收尾交接清单（重型 Harness + 学习域 rails 服务端）

> 关联规格：`docs/HEAVY_HARNESS_SPEC.md`（设计与决策定稿）、`docs/GLOBAL_HARNESS_SPEC.md`（v1 轻量 Harness）。
> 状态：**服务端全部落地并通过全量编译**；前端轨（§13 契约）与运行期 E2E 为剩余工作。

## 1. 总体状态

| 域 | 状态 | 说明 |
|---|---|---|
| 重型 Harness P0–P5 | ✅ 服务端 | PolicyEngine/动作级HITL/角色矩阵 → 工具元数据+壳化网关+pipelineToken → 意图两级 → 预算+LLM摘要 → 会话级任务板 |
| AI 自测（KB 化·透题红线） | ✅ | exercise/assess 节点去题库化；角色矩阵 `exam.*:2` 确定性封题库 |
| 作业 / 考试（后端） | ✅ | 出卷骨干（exam_paper 快照+确定性判分）→ 发布排期（biz_type 作业/考试）→ 窗口交卷 → 记录回流 learning（source 3/4） |
| 考试 Kafka 提交轨（消费端） | ✅ | exam_submission 幂等落库 + 异步判分 + 回流（topic `lms-exam-submission`） |
| lms-learning 防滥用 | ✅ | 做题 200/天、测评 30/天 |
| 前端轨（日历/答题/考试锁页/发消息） | ⏳ | 后端就绪 + 对接契约 §13；lms-web 待实现页面 |
| 运行期 E2E | ⏳ | 需 Redis/Nacos/MySQL/Kafka/DashScope key 联调 |

## 2. 关键开关（回退路径）

- `lms.ai.harness-v2.enabled=false`：重型 Harness 整体回退 v1 行为；
- `lms.ai.harness-v2.tool-gateway-enabled=false`：工具壳化回退直注 Bean（P0 行为）；
- `lms.ai.harness-v2.intent-llm-enabled`（默认 false）开启 LLM 意图分类；
- `lms.ai.harness-v2.summary-enabled`（默认 false）开启 LLM 会话摘要压缩；
- `lms.ai.harness-v2.max-write-calls / max-invites`（默认 0=不限）开启写/邀请预算；
- v1 `lms.ai.harness.*` 保持原语义（invite 高危 ask 默认）。

## 3. 需要执行/同步的配置与迁移（上线前）

1. **SQL**（新库 init 自动执行；存量库手动执行）：
   - `docker/mysql/init/06-lms-exam.sql`：新增 `exam_schedule`、`exam_paper`、`exam_paper_item`、`exam_submission`；
   - `docker/mysql/init/91-v03-agent-task.sql`：`agent_task` 加列（session/parent/intent/action/depth）+ 索引（存量库跑一次）；
2. **Nacos**：
   - `lms-gateway.yaml`：lms-exam 路由已含 `/admin/exam-schedules/**`、`/exam-schedules/**`、`/admin/exam-papers/**`、`/exam-papers/**`；
   - `lms-exam.yaml`：已加 `spring.kafka.*`（消费端）；topic 覆盖键 `lms.exam.submission-topic`；
   - lms-exam 需发布到 Nacos（本地 application.yml 与 nacos 冲突时以 Nacos 为准）。
3. **模型 key**：`DASHSCOPE_API_KEY` 注入（qwen-turbo 意图分类/摘要、qwen-max/plus 编排）。

## 4. 本轮新增/修改模块地图（服务端）

- **lms-ai**：`harness/`（PolicyEngine/PolicyDecision/ActionType/HitlService 动作级/PipelineTokenService/ToolMetaService/HarnessKeys/Trace 扩展）、`intent/`（Intent/IntentDecision/IntentRouterService/IntentClassifierService）、`llm/ModelCaller`、`chat/ContextCompressor`、`tools/`（ToolDomainRegistry/ToolControlService/GuardedFunctionTool/AnnotatedToolMetaService）、`context/SessionLink`、`task/TaskService`（任务板方法）、`config/HarnessV2Properties`、`orchestration/LearningPipelineService`（KB 出题/包判分/任务板）、`agents/exercise-agent.yaml`、`agents/assess-agent.yaml`；
- **lms-exam**：`ExamSchedule*`（发布物排期）、`ExamPaper*`（试卷快照）、`util/PaperGrader`、`mq/ExamSubmissionConsumer`、`client/LearningRecordClient`、`config/UserInfoFeignConfig`、`config/AsyncUser`、pom +openfeign；
- **lms-learning**：`EvalDataServiceImpl` 日上限、`ExerciseRecord` 来源常量（3 作业/4 考试）；
- **lms-web**（待做）：见 §6。

## 5. 已知注意点（实现取舍，联调/演进时注意）

1. 动作级 HITL：默认无 risk=high 工具（`tool-risk` 表默认空）→ ask 仅对高危 agent 邀请生效；`im.pushMessage` 流归属歧义（老师直推 vs 学生问老师）待"流上下文"细化后再定工具级 ask（规格 §12 备注）；
2. 作业/考试回流 courseId 取排期**首课程**归集（多课程排期粒度粗，前端可后续按学生课程细化）；
3. 考试提交轨上线后**考试作答改走 Kafka**，同步 submit 留给作业，避免双通道重复回流（规格 §13.3）；
4. AI 自测题目包契约：前端渲染去答案题目、交卷带 `questionId(=qid)`；assess 按本课程最近题目包判分（单包语义）；
5. `agent_task` 会话落板仅数字会话（eval- 非数字不落板，管道经 SessionLink 挂真实会话）。

## 6. 前端轨状态（lms-web，对接契约 §13）

**已实现（代码，待本机构建验证）**：
- [x] `api/exam.js` 增补：listMineSchedules / getSchedule / getPaper / submitSchedule / submitScheduleAsync；
- [x] `views/ExamScheduleView.vue`：我的考试/作业列表（课程 ids 过滤 + 作业/考试分页签）；
- [x] `views/ExamPaperView.vue`：卷面作答（去答案渲染，作业即交即判可重做；考试锁页：blur/切后台警告 3 次或离场累计 30s 自动交卷 → submitScheduleAsync）；
- [x] 后端异步提交端点：`POST /exam-schedules/{id}/submit-async`（考试窗口校验 → Kafka 幂等消费端）；
- [x] `router/index.js` 路由注册（/exam-schedules、/exam-papers/:paperId、/admin/papers）；
- [x] `views/TeacherPaperView.vue`：老师出卷工作台（题库选题组卷→发布卷面→发布作业/考试排期→我的卷面/排期管理+预览含答案）；
- [x] SFC 编译校验：3 个新增视图经 `@vue/compiler-sfc` 编译通过；router/api `node --check` 通过（`lms-web/scripts/check-sfc.mjs` 可复用）；
- [x] 入口/导航：Home 学习工作台 + 教师工作台入口卡（我的考试/作业、出卷/发布），App 顶部导航新增 考试/作业、出卷；
- [x] 作业/考试结果展示：lms-learning `GET /learn/stats/homework-exam`（来源 3/4 聚合 + 最近记录），Home 学习概览区块展示；
- [ ] `npm run build` 完整产物验证：**沙箱拒绝 esbuild 子进程（EPERM）且提权被否**——需在用户本机执行 `cd lms-web && npm run build` 验证；
- [ ] 老师端组卷工作台 UI（后端 exam-papers 组卷/发布接口已就绪）；
- [ ] Home/导航入口链接（可选）。

## 7. 运行期 E2E 清单（联调时逐项过）

- [ ] HITL：老师会话邀请 exam-agent → ASK（requestId）→ approve → 重试放行；reject → 窗口内拒绝；
- [ ] 角色矩阵：学生直调/邀请 exam.* 被 DENY（Trace code=ROLE/WHITELIST）；
- [ ] 工具开关：Redis `tool:switch:{toolId}=off` → 工具不可用提示；
- [ ] 意图：`intent-llm-enabled=true` 后命中/兜底率（Trace intent_route）；
- [ ] 摘要：summary-enabled=true 长会话触发压缩且 QA 不回退；
- [ ] 任务板：诊断→规划→习题→测评 任务树可见；turn/邀请/写工具行存在；
- [ ] 考试 Kafka：发 topic → exam_submission 幂等落库 + 判分 + learning source=4；重复消息跳过；
- [ ] 回归：/agent/chat、IM 回流、ask-teacher、qa_remind、签到/积分、抢课等既有主链路不回退。
