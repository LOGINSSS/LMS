# 运行/完成统一清单（RUN CHECKLIST）

> 范围：v1 收尾（重型 Harness P0–P5 + 学习域 rails）+ 新增【选课资格约束 + 排课/日历】
> 本清单 = 你本机"跑起来 + 调通 + 收尾"的一站式步骤；[x] 已由我完成（编译级验证），[ ] 需你在本机执行/微调。

## 0. 依赖与一键启停（参考 scripts/）

- [x] MySQL 8（docker，端口 13306，root/root）、Redis(6379/123456)、Nacos(8848)、Kafka(29092)
- [x] 服务注册/配置走 Nacos；`docker-compose.yml` 启动中间件后，`scripts/` 一键起服务（按序见 §2）

## 1. 数据库迁移（存量库手动执行；新库 init 自动）

- [ ] 执行新增/更新 SQL（按文件名序）：
  - `docker/mysql/init/06-lms-exam.sql`（exam_schedule / exam_paper / exam_paper_item / exam_submission）
  - `docker/mysql/init/91-v03-agent-task.sql`（agent_task 会话任务板列）
  - `docker/mysql/init/92-v04-course-access.sql`（course_enroll_rule 选课资格规则）
  - `docker/mysql/init/93-v05-course-schedule.sql`（course_schedule_slot 排课模板）
- [x] 新库首次 init 自动包含；已跑过的库：`source` 以上文件一次（幂等由列/表不存在保证，重复执行报已存在可忽略）

## 2. Nacos 配置同步（nacos-config/ 目录 → Nacos）

- [ ] 发布到 Nacos（与目录同名 dataId）：
  - `lms-gateway.yaml`（已含 lms-calendar / lms-exam exam-papers/schedules 路由）
  - `lms-course.yaml` / `lms-exam.yaml` / `lms-learning.yaml` / `lms-grab.yaml` / `lms-calendar.yaml`(新增)
  - `lms-ai.yaml`（保持原有；harness-v2 开关见本地 application.yml 注释）
- [ ] `DASHSCOPE_API_KEY` 注入 lms-ai 环境变量（意图 LLM 分类/摘要/Agent 编排）

## 3. 服务启动顺序（各自模块 `java -jar` 或 IDE）

1. 基础设施（MySQL/Redis/Nacos/Kafka）→ Nacos 配置发布完成
2. `lms-gateway`(8080) → `lms-auth`/`lms-user` → `lms-course` → `lms-media`/`lms-remark`/`lms-search`
3. `lms-exam` → `lms-learning` → `lms-kb` → `lms-grab` → `lms-statistics`
4. `lms-ai`(8095) → `lms-calendar`(8098，新增)
5. 前端：`cd lms-web && npm install && npm run dev`（VITE_API_BASE=/api 或直连网关 8080）

## 4. 本会话已完成并编译验证（代码级）

| 域 | 内容 | 验证 |
|---|---|---|
| 重型 Harness | P0 PolicyEngine/动作级HITL/角色矩阵；P1 工具网关/元数据/开关/pipelineToken；P2 意图两级；P3 壳化；P4 预算+LLM摘要；P5 会话任务板 | lms-ai compile ✅ |
| 学习域 rails | AI 自测 KB 化（透题红线 exam.*:2）；出卷骨干(exam_paper+确定性判分)；发布物排期(作业/考试+交卷)；考试 Kafka 提交消费端；记录回流 source3/4 | lms-exam ✅ lms-learning ✅ |
| 防滥用 | 做题 200/天、测评 30/天 | lms-learning ✅ |
| **选课资格约束(P1)** | course_enroll_rule + EligibilityService(规则引擎: grade/major/college/points_min/enrolled/course_done，AND/OR) + enroll 门禁 + **grab Redis 预检前强校验(fail-closed)** + 课程广场 eligibility 提示接口 + 教师规则 CRUD | lms-course ✅ lms-grab ✅ |
| **排课面(P2)** | course_schedule_slot（每周/单周/双周/单次+例外日）+ 教师排课 CRUD + `/courses/schedule` 日期区间展开 | lms-course ✅ |
| **统一日历(P3)** | 新模块 lms-calendar：聚合 排课(class)+考试/作业(exam_schedule) → EventVO(type/color/jump) + Redis 缓存；路由 /calendar/** | lms-calendar ✅ |
| 前端(部分) | 考试/作业列表、卷面答题(作业即判可重做/考试锁页→Kafka 异步)、老师出卷工作台、**主页大日历(月+周课表翻页、多色、跳转)**、入口/导航链接、作业考试结果学习概览 | SFC 编译 ✅（`npm run build` 需本机跑） |
| 全量 | 根 reactor（含 lms-calendar）clean compile | ROOT_EXIT=0（见进程日志） |

## 5. 需要你本机完成/微调（[ ] 项）

1. [ ] `cd lms-web && npm run build`（沙箱拒 esbuild 子进程，需本机验证产物；可用 `node scripts/check-sfc.mjs src/views/*.vue` 快速回归）
2. [ ] 联调 E2E（清单见 §6）：HITL、角色矩阵 DENY、资格门禁（enroll/grab）、考试 Kafka 幂等、任务板、意图/摘要、日历多色渲染
3. [ ] 前端补齐（可选 UI 细化）：
   - 课程广场/详情：报名按钮按 `GET /courses/{id}/eligibility` 显示可报/灰态+原因
   - 教师：课程规则配置 UI（`/admin/courses/{id}/enroll-rule`）+ 排课管理 UI（`/admin/courses/{courseId}/slots`）
   - 主页大日历入口已加；周课表联动/性能懒加载可按需精修
4. [ ] 微调点（已在代码注释标注）：
   - 单周/双周口径：`ScheduleServiceImpl.expand` 现按自然周序号奇偶约定，如与校历（学期第几周）不符改 parity 实现
   - `enrolled`=在读即可；`course_done`=在读+进度100%（改 `EligibilityService.checkEnrolled` 一处可换口径，如按测评通过）
   - 档案字段名 `grade/major/college` 按 lms-user `/users/{id}` 返回键名（`profileField`）
   - 考试双通道：考试提交轨上线后考试走 Kafka 异步（`submit-async`），同步 submit 留给作业（避免重复回流）
   - 日历架构可选拆分：现 lms-calendar 独立模块已按推荐落地；如需事件总线级缓存失效再升级

## 6. E2E 校验点（跑通后逐条过）

- [ ] 登录学生 A（大三）+ 学生 B（大二）；老师建课并设规则 `grade:["大三"]`（POST /admin/courses/{id}/enroll-rule）
  - A 报名成功；B 报名被拒（enroll 返回原因）；课程详情 eligibility：B allowed=false+reasons
  - 设抢课窗口：B 抢课被拒（grab 预检前拦截）；A 抢课正常
- [ ] 规则组合：mode=ANY + points_min/enrolled/course_done 各命中/未命中场景
- [ ] 排课：老师为课程加 每周三 10:00 模板 → `/courses/schedule?courseIds=1&start&end` 展开出现每周三上课事件；加单次调课行生效
- [ ] 日历：`GET /calendar/mine` 返回 class/exam 事件与颜色/跳转；主页日历月/周视图渲染、翻页、点击跳转
- [ ] 作业/考试：出卷→发布→学生答题（作业重做；考试锁页→Kafka 消费端幂等落库 exam_submission + 回流 learning source=4）
- [ ] Harness：高危邀请 HITL approve/reject；学生触 exam.* DENY；工具动态关停；意图/摘要开关；任务树可见
- [ ] 回归：/agent/chat、IM 回流、ask-teacher、qa_remind、签到积分、抢课主链路不回退

## 7. 新增接口速查

- 资格：`GET /courses/{id}/eligibility`；`GET|POST /admin/courses/{id}/enroll-rule`、`POST .../remove`
- 排课：`POST|GET /admin/courses/{courseId}/slots`、`DELETE .../slots/{slotId}`；`GET /courses/schedule?courseIds&start&end`
- 日历：`GET /calendar/mine?courseIds&start&end`（经网关 /calendar/**）
- 作业/考试/试卷：`/exam-schedules/**`、`/exam-papers/**`、`/admin/exam-schedules/**`、`/admin/exam-papers/**`（详见 HEAVY_HARNESS_SPEC §13）
- 异步提交：`POST /exam-schedules/{id}/submit-async`（考试 → Kafka topic lms-exam-submission）
