# LMS - 微服务练手项目

基于 Spring Cloud Alibaba 的微服务练手项目：课程学习平台，覆盖「认证 → 课程 → 媒资 → 评价互动 → 搜索 → 题库 → 学习过程 → 数据看板 → AI 对话」完整业务链路。前后端分离，每个业务模块**独立库 `lms_<domain>`、独立 Nacos 配置、经网关统一鉴权路由**。

架构与版本体系对齐 `java-microservice-structure` 技能包。

## 技术栈

| 类别 | 选型 | 版本 |
|---|---|---|
| JDK | Java 17（本机 JDK 21 可编译运行） | 17 |
| 微服务框架 | Spring Boot / Spring Cloud | 3.3.5 / 2023.0.3 |
| 注册·配置中心 | Spring Cloud Alibaba Nacos | 2023.0.3.2（服务端 v2.3.2） |
| ORM | MyBatis-Plus | 3.5.9 |
| 消息队列 | Kafka（KRaft 单节点） | 3.8.0 |
| 缓存 | Redis + Redisson | 7.2.5 / 3.34.1 |
| 搜索引擎 | Elasticsearch | 8.13.4 |
| 数据库 | MySQL | 8.0.36 |
| 网关 | Spring Cloud Gateway | 随 Cloud BOM |
| AI 框架 | AgentScope Java（多智能体） | 2.0.2 |
| LLM 后端 | 通义千问 DashScope（OpenAI 兼容模式） | — |
| 工具 | hutool / Knife4j | 5.8.36 / 4.5.0 |

## 架构分层（点击模块名直达模块文档）

```
┌─ 接入层 ───────────────────────────────────────────────┐
│ lms-gateway 网关（路由/JWT 鉴权） · lms-auth 认证       │
├─ 业务层（每模块独立库 lms_<domain>）───────────────────┤
│ lms-user · lms-course · lms-media · lms-remark        │
│ lms-search · lms-exam · lms-learning · lms-statistics  │
│ lms-ai · lms-kb · lms-grab                            │
├─ 公共层 ───────────────────────────────────────────────┤
│ lms-common（统一响应/异常/分页/JWT/UserContext/自动配置）│
├─ 前端 ─────────────────────────────────────────────────┤
│ lms-web（Vue3 + Vite）                                │
└─ 基础设施 ─────────────────────────────────────────────┘
   docker-compose（nacos/mysql/redis/kafka/es）· nacos-config · scripts
```

### 接入层

| 模块 | 端口 | 职责 | 文档 |
|---|---|---|---|
| lms-gateway | 8080 | 统一入口：路由转发（lb://）、JWT 统一鉴权、白名单放行、CORS、user-info 头透传 | [lms-gateway/README.md](lms-gateway/README.md) |
| lms-auth | 8085 | 注册 / 登录 / 登出 / JWT 签发，账号与登录日志（库 lms_auth） | [lms-auth/README.md](lms-auth/README.md) |

### 业务层

| 模块 | 端口 | 库 / 存储 | 职责 | 文档 |
|---|---|---|---|---|
| lms-user | 8086 | lms_user | 用户档案：公共字段 + 学生 / 教师扩展，管理端分页 | [lms-user/README.md](lms-user/README.md) |
| lms-course | 8087 | lms_course | 课程卡片展示、教师建课与上下架、学生选课退课 | [lms-course/README.md](lms-course/README.md) |
| lms-media | 8088 | lms_media | 文件 / 视频上传与媒资管理，统一存储抽象（本地磁盘 → 可换 OSS） | [lms-media/README.md](lms-media/README.md) |
| lms-remark | 8090 | lms_remark | 跨业务对象（课程 / 笔记 / 问答）的通用点赞互动 | [lms-remark/README.md](lms-remark/README.md) |
| lms-search | 8091 | ES 索引 course + lms_search | ES 课程搜索 / 按兴趣标签推荐 / 标签上报 | [lms-search/README.md](lms-search/README.md) |
| lms-exam | 8092 | lms_exam | 题目管理（单选 / 多选 / 判断）、题库业务绑定 | [lms-exam/README.md](lms-exam/README.md) |
| lms-learning | 8093 | lms_learning | 课次 / 学习记录 / 笔记 / 互动问答 / 签到 / 积分与积分榜 | [lms-learning/README.md](lms-learning/README.md) |
| lms-statistics | 8094 | lms_statistics | 数据看板：总览 / 今日数据 / Top10，跨服务 Feign 聚合 | [lms-statistics/README.md](lms-statistics/README.md) |
| lms-ai | 8095 | lms_ai（L1 会话/L2 画像/任务）+ Redis | AgentScope Java + DashScope：单轮对话 + **个人 Agent 体系**（个人/子 Agent、三层记忆、邀请制编排、IM 管道、定时任务）+ 学习评测管道（0.2 工具面扩展） | [lms-ai/README.md](lms-ai/README.md) |
| lms-kb | 8096 | lms_kb + ES lms_kb_chunk | 知识库（课程/个人）+ RAG 五步流水线 + RAGAS 评测；0.2 课程正文同步入知识库 | [lms-kb/README.md](lms-kb/README.md) |
| lms-grab | 8097 | lms_grab + Redis | **抢课（0.2 新增）**：Redis Lua 预检库存 + Kafka 异步落库 + 对账回补 | — |

### 公共层

| 模块 | 说明 | 文档 |
|---|---|---|
| lms-common | 所有服务的公共底座：统一响应 / 异常体系、分页、工具类、JWT、用户上下文、MyBatis-Plus / Swagger / MVC 自动配置 | [lms-common/README.md](lms-common/README.md) |

### 前端

| 模块 | 端口 | 说明 | 文档 |
|---|---|---|---|
| lms-web | 5173 | Vue3 + Vite 单页应用：登录注册 / 课程 / 媒资 / 搜索 / 看板 / 学习中心 / 题库管理 | [lms-web/README.md](lms-web/README.md) |

### 基础设施与编排（详见「快速开始」）

- `docker-compose.yml`：nacos / mysql / redis / kafka / es（可选 kafka-ui / kibana）
- `docker/mysql/init/`：MySQL 首次启动自动执行的建库脚本（每服务独立库 `lms_<domain>`）
- `nacos-config/`：配置中心内容源（13 份 yaml），`scripts/push-nacos-config.ps1` 一键导入
- `scripts/`：`start-all.ps1` / `stop-all.ps1`（一键启停全部服务）、`cleanup-demo-data.sql`

## 快速开始

### 1. 启动基础设施（需 Docker Desktop）

```bash
docker compose up -d                     # 核心：nacos/mysql/redis/kafka/es
docker compose --profile tools up -d     # 可选：+ kafka-ui/kibana
docker compose ps                        # 查看状态（等所有容器 healthy）
```

### 2. 导入 Nacos 配置（首次）

```powershell
powershell -ExecutionPolicy Bypass -File scripts/push-nacos-config.ps1
```

### 3. 构建并启动应用

```bash
mvn -s mvn-settings.xml clean install    # 构建全部模块（common → gateway → 业务模块）
```

- IDEA：直接运行各服务 `*Application`，profile 选 `dev`（先保证 Nacos 已 up）
- 一键启动：`powershell -ExecutionPolicy Bypass -File scripts/start-all.ps1`（启动 10 个后端 jar + Vite）
- 前端：`cd lms-web && npm install && npm run dev`

> 启动顺序：先 `docker compose up -d`（等 healthy）→ 再启网关与业务服务。前端直连网关 8080，跨域已由网关 CORS 放行。

## 服务地址

| 服务 | 地址 | 账号 |
|---|---|---|
| Nacos 控制台 | http://localhost:8848/nacos | nacos / nacos（练手环境已关鉴权） |
| MySQL | localhost:13306 | root / root（业务库 `lms_auth`/`lms_user`/`lms_course`...，默认库 `lms`） |
| Redis | localhost:6379 | 密码 `123456` |
| Kafka（宿主机应用连接） | localhost:29092 | — |
| Kafka（容器内服务连接） | lms-kafka:9092 | — |
| Elasticsearch | http://localhost:9200 | 已关安全认证 |
| kafka-ui（可选） | http://localhost:9000 | — |
| Kibana（可选） | http://localhost:5601 | — |
| 网关 | http://localhost:8080 | — |
| 前端 | http://localhost:5173 | — |

> 注意：MySQL 宿主机端口为 **13306**（容器内 3306），IDEA 里跑服务连数据库用 `localhost:13306`；3306 是本机原生 MySQL。

## 目录结构

```
LMS/
├── pom.xml                  # 父工程：聚合模块 + 统一版本管理（唯一版本源）
├── mvn-settings.xml         # 项目专用 Maven settings（localRepository 指向 .m2-repo）
├── docker-compose.yml       # 基础设施编排：nacos/mysql/redis/kafka/es（+ kafka-ui/kibana）
├── docker/mysql/init/       # MySQL 首次启动自动建库脚本（每服务独立库 lms_<domain>）
├── nacos-config/            # 配置中心内容源（lms-common/gateway/auth/... 共 13 份 yaml）
├── scripts/                 # 运维脚本：push-nacos-config / start-all / stop-all
├── lms-common/              # 公共库：统一响应/异常/分页/工具/JWT/自动配置 → README
├── lms-gateway/             # 网关：路由 + JWT 鉴权 + 白名单 + user-info 透传 → README
├── lms-auth/                # 认证：注册/登录/登出/JWT（库 lms_auth）→ README
├── lms-user/                # 用户档案：学生/教师扩展（库 lms_user）→ README
├── lms-course/              # 课程：卡片展示/建课/选课（库 lms_course）→ README
├── lms-media/               # 媒资：上传/管理/存储抽象（库 lms_media）→ README
├── lms-remark/              # 评价互动：通用点赞（库 lms_remark）→ README
├── lms-search/              # 搜索：ES 搜索/推荐/兴趣标签（ES + lms_search）→ README
├── lms-exam/                # 题库：题目管理/业务归类（库 lms_exam）→ README
├── lms-learning/            # 学习过程：课次/记录/笔记/问答/积分/签到（库 lms_learning）→ README
├── lms-statistics/          # 数据中心：看板/今日数据/Top10（库 lms_statistics）→ README
├── lms-ai/                  # AI 能力：AgentScope + DashScope 对话 → README
├── lms-web/                 # 前端：Vue3 + Vite（登录/课程/媒资/搜索/看板/学习/题库）→ README
└── docs/                    # 设计文档（业务模块蓝图 / 代码注释规范）
```

## 相关文档

- [个人 Agent 体系与三层记忆编排规范](docs/PERSONAL_AGENT_SPEC.md)：每人一个 agent + 三层记忆 + 跨 agent 邀请制编排 + IM 管道/定时任务（**阶段 1~4 核心已实现**：lms-ai Agent 运行时 + lms-kb 个人知识库 owner 扩展；阶段 5 加固为后续项）
- [六大业务模块设计蓝图](docs/BUSINESS_MODULES.md)：媒资 / 搜索 / 学习 / 考试 / 数据中心 / 评价互动的设计依据
- [前后端接口契约（JSON Schema）](docs/api-schemas/README.md)：与 Java DTO 一一对应的接口数据结构定义
- [代码注释规范](docs/CODE_COMMENT_STYLE.md)
- [MySQL 建库脚本说明](docker/mysql/init/README.md)
