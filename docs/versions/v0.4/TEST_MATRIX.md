# v0.4 最小测试矩阵

更新时间：2026-09-10；环境：Windows 本机、Docker 基础设施、LMS 全服务、Python RAG、真实模型接口。

## 指标口径

| 指标 | 口径 |
|---|---|
| 吞吐 | 成功与失败请求总数 / 实测墙钟时间；必须同时给出并发数、样本量和目标接口 |
| P95 | 单请求端到端耗时的 95 百分位；AI SSE 以收到完整 `done` 为结束 |
| 超卖数 | 对每门课程计算 `max(有效选课数 - 配置库存, 0)` 后求和 |
| Kafka 积压 | 指定消费组各分区 `LOG-END-OFFSET - CURRENT-OFFSET` 之和 |
| AI 成功率 | HTTP 成功且 SSE 收到 `done`、未收到 `error` 的请求占比 |

## 本次实测结果

| 链路 | 样本与并发 | 吞吐 | P95 | 成功率/一致性 | 结论等级 |
|---|---:|---:|---:|---:|---|
| 课程分页 `GET /courses/page` | 1000 / 50 | 984.472 req/s | 79.086 ms | 100% | 当前机器短时基线 |
| AI Copilot `POST /agent/chat/stream` | 5 / 1 | 1.683 req/s | 770.388 ms | 100% | 小样本可用性，不代表容量上限 |
| 历史课程读压测 baseline | 457616 / 阶梯 50→800 | 1522.595 req/s | 376 ms | 0 错误 | 仅历史读链路证据 |
| 历史课程读压测 smoke | 68659 / 报告原配置 | 430.623 req/s | 81 ms | 0 错误 | 仅历史读链路证据 |
| 抢课回执闭环 | 1 门库存 1 课程 / 1 个真实学生 | — | 未采集 | Redis `1→0`；记录 `PENDING→LANDED`；唯一有效选课 1；重复请求不新增 | 真实功能闭环，不代表并发容量 |
| 抢课数据一致性 | 闭环后库快照 | — | — | 超卖 0；重复有效选课 0；超时待回执 0；`LANDED` 无选课 0；有效选课仍 `PENDING` 0 | 真实单样本一致性 |
| Kafka `lms-course-grab` | 1 个分区 | — | — | lag 0（offset 2/2） | 已消费真实抢课事件 |
| Kafka `lms-grab-landed` | 1 个分区 | — | — | lag 0（offset 1/1） | 已消费真实落地回执 |
| 教师角色鉴权 | 4 个单元测试 + HTTP 检查 | — | — | 学生访问教师入口均为 403 | 已验证 |
| RAG 隔离 E2E | 1 次写入→问答→删除 | — | 6404 ms（问答） | 1/1 成功，返回 1 个来源，清理后 0 行 | 真实模型小样本 |
| RAG 清库状态校正 | 23 个旧文件 + Milvus 真值 | — | — | 23 个均为 `needs_reingest`；实际 chunk 总数 0；未自动重建 | 真实运行态 |
| HITL | 1 次高风险邀请→拒绝 | — | 6813 ms | 状态从待处理变为拒绝，未产生考试 | 真实链路小样本 |

历史 JTL 的统计由原始采样重新计算。baseline：平均 124.792 ms、P50 79 ms、P99 507 ms、持续 300.550 s；smoke：平均 29.803 ms、P50 22 ms、P99 119 ms、持续 159.441 s。旧 HTML 报告中的部分汇总值与原始 JTL 不一致，因此不采用其错误字段。

## 最小回归矩阵

| 层级 | 用例 | 通过条件 |
|---|---|---|
| 单元 | `GrabRecordStateServiceTest` | 只允许 `PENDING → LANDED`，重复回执和已回补记录不被复活 |
| 单元 | `GrabReconcileSchedulerTest` | 超时记录只重投事件，不盲目回补库存 |
| 单元 | `AgentControllerAuthorizationTest` | 学生不能使用任何教师 Agent 入口 |
| 集成 | 抢课事件→选课→落地回执 | 选课唯一、记录最终 `LANDED`、两个消费组 lag 回落为 0 |
| 并发 | 多用户争抢同一课程 | 成功数不超过库存、超卖数 0、重复有效选课 0 |
| AI | Copilot SSE | 有 `stage/meta/delta/done`，失败时只有脱敏 `error` |
| AI | HITL | 高风险工具在批准前不执行，拒绝后不可执行 |
| AI | 任务树 | 会话出现真实 turn/invite/tool/pipeline 节点及最终状态 |
| RAG | 写入→检索→清理 | 命中隔离课程知识、来源可追溯、清理后无残留 |
| RAG | 文件状态与课程评测单元测试（8 个） | 批量计数、强一致实时总数、缺失/不完整向量语义、课程 RAGAS 数据契约全部通过 |

## 复现方式

短时 HTTP 基线不依赖 JMeter：

```powershell
$env:LMS_USERNAME='s_demo'
$env:LMS_PASSWORD='<本地测试密码>'
$env:TARGET_PATH='/courses/page?pageNo=1&pageSize=10'
$env:REQUESTS='1000'
$env:CONCURRENCY='50'
$env:SUCCESS_MODE='lms-json'
node scripts/perf/http-bench.mjs
```

完整阶梯压测继续使用 `scripts/jmeter/lms-loadtest.jmx`。本机当前未发现 JMeter 命令，但 JMX 已具备，无需插件；安装 JMeter 5.6.x 后以 `-Jtoken` 临时注入登录 Token。正式抢课容量测试还需要一批相互独立的学生账号/Token，不能用同一用户绕过“用户去重”口径。
