# v0.4 简历 Top 4 亮点

## 推荐项目简介

AI 驱动的课程学习平台。基于 Spring Cloud Alibaba 构建 14 个可运行服务，覆盖认证、课程、媒资、互动、检索、题库、学习、数据看板、抢课与 AI Copilot；结合 AgentScope Java 多 Agent Harness、Python RAG、HITL 审批和可观测任务树形成可验证的智能学习闭环。

## 推荐亮点

1. 设计并实现多 Agent Harness：以 2 个角色个人 Agent 编排 15 个领域/流程 Agent，通过声明式注册表、受控工具网关、邀请深度与预算策略、HITL 审批及任务 Trace 约束执行；将 Feign 业务能力封装为 Agent 工具，并在 Copilot 中展示流式阶段、审批卡和任务树。
2. 设计并实现高并发抢课可靠链路：Redis 预热窗口与库存，Lua 原子完成窗口校验、用户去重和扣减，Kafka 异步幂等落选课；新增反向落地回执驱动 `PENDING → LANDED`，超时采用安全重投而非盲目回补，规避迟到消息导致的超卖窗口。
3. 设计并实现独立 Python RAG 服务：以 Milvus、DashScope embedding 和生成模型完成文档/图片解析、OCR/VLM 文本化、向量检索、来源回传与评估接口；通过隔离知识写入→真实问答→删除闭环验证外部模型和向量库可用性。
4. 从零搭建 Spring Cloud Alibaba 微服务系统：15 个 Maven 子模块（14 个可运行服务）按业务域独立库与 Nacos 配置，经 Gateway 统一 JWT 鉴权和用户上下文透传；使用 Docker Compose 与脚本编排 MySQL、Redis、Kafka、Elasticsearch、Nacos、Milvus 及应用服务。

## 量化数据的使用规则

- 可以写：课程分页短时基线在 50 并发、1000 请求下为 984.472 req/s，P95 79.086 ms，成功率 100%。
- 可以写：历史课程读链路 457616 请求、0 错误，吞吐 1522.595 req/s，P95 376 ms；必须注明是“课程读链路”。
- 暂不写“抢课万级 QPS”或“压测零超卖”：当前只有静态一致性 0 异常，仍缺多账号并发抢课数据。
- AI 的 5 次 SSE 与 RAG/HITL 单次 E2E 只能证明可用性，不能包装成稳定性 SLA。

这四点比“Redis 多类缓存 + ZSET 榜单”更适合作为 Top 4：后者是合格工程能力，但区分度低，可在面试追问或项目补充项中展开。
