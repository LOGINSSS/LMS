# LMS - 微服务练手项目

基于 Spring Cloud + Spring Boot 的微服务练手项目（架构与版本体系对齐 `java-microservice-structure` 技能包）。

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

## 目录结构

```
LMS/
├── pom.xml                  # 父工程：聚合模块 + 统一版本管理（唯一版本源）
├── docker-compose.yml       # 基础设施：nacos/mysql/redis/kafka/es（+ kafka-ui/kibana）
├── docker/mysql/init/       # MySQL 首次启动自动执行的建库脚本（每服务独立库 lms_<domain>）
├── nacos-config/            # 配置中心内容（lms-common/lms-auth/lms-user/lms-gateway.yaml）
├── scripts/                 # push-nacos-config.ps1 等运维脚本
├── lms-common/              # 公共库（library）：统一响应/异常、分页、hutool 工具、自动配置
├── lms-gateway/             # 网关：路由（lb://）、统一 JWT 鉴权过滤器、user-info 透传
├── lms-auth/                # 认证服务：注册/登录/登出/JWT 签发/登录日志（库 lms_auth）
├── lms-user/                # 用户服务：学生/教师档案、管理端分页（库 lms_user）
├── lms-ai/                  # AI 能力服务：AgentScope Java + DashScope（LLM 接入）
└── ...业务模块（lms-course / ...）后续逐模块添加
```

## 快速开始

### 1. 启动基础设施（Docker Desktop）

```bash
docker compose up -d                     # 核心：nacos/mysql/redis/kafka/es
docker compose --profile tools up -d     # 可选：+ kafka-ui/kibana
docker compose ps                        # 查看状态（等所有容器 healthy）
```

### 2. 服务地址

| 服务 | 地址 | 账号 |
|---|---|---|
| Nacos 控制台 | http://localhost:8848/nacos | nacos / nacos（练手环境已关鉴权） |
| MySQL | localhost:13306 | root / root（业务库 `lms_auth`/`lms_user`，默认库 `lms`） |
| Redis | localhost:6379 | 密码 `123456` |
| Kafka（宿主机应用连接） | localhost:29092 | — |
| Kafka（容器内服务连接） | lms-kafka:9092 | — |
| Elasticsearch | http://localhost:9200 | 已关闭安全认证 |
| kafka-ui（可选） | http://localhost:9000 | — |
| Kibana（可选） | http://localhost:5601 | — |
| 网关 | http://localhost:8080 | — |
| 认证服务（lms-auth） | http://localhost:8085 | — |
| 用户服务（lms-user） | http://localhost:8086 | — |
| AI 服务（lms-ai） | http://localhost:8090 | 需配置 DashScope API Key |

> 注意：MySQL 宿主机端口为 **13306**（容器内 3306），IDEA 里跑服务连数据库用 `localhost:13306`；3306 是本机原生 MySQL。

### 3. 构建与启动应用

```bash
mvn clean install              # 构建全部模块（common → gateway → 业务模块）
mvn -pl lms-gateway spring-boot:run          # 单独启动网关（先保证 Nacos 已 up）
# IDEA 中直接运行 GatewayApplication，profile 选 dev
```

> 启动顺序：先 `docker compose up -d`（等 healthy）→ 再启动网关/业务服务。

## 登录模块（老师 / 学生）

### 架构与数据边界

```
前端 ──▶ gateway(8080) ── JWT 校验 + 白名单 ──▶ lms-auth(8085)  /  lms-user(8086)
                    │                                  │                 │
                    │                              lms_auth 库      lms_user 库
                    │                              ├ account        ├ user
                    │                              └ login_log      ├ teacher_info（教师扩展）
                    │                                              └ student_info（学生扩展）
                    └── JWT 密钥 / Redis / 路由 / 白名单全部在 Nacos
```

- **lms-auth（认证服务）**：凭据与登录。`account` 存账号（密码 BCrypt，`user_type` 1=学生 2=教师）；`login_log` 存登录日志。签发 JWT（含 userId/userType/username/jti），登出把 jti 写 Redis 黑名单。
- **lms-user（用户服务）**：档案。`user` 存公共档案（不含密码），按 `user_type` 扩展 `teacher_info` / `student_info`，实现老师学生字段分离。
- **lms-gateway（网关）**：`AuthGlobalFilter` 统一校验 JWT（白名单放行登录/注册/文档路径），校验通过后把 `user-info` 头（`{"userId":..,"userType":..}`）透传给下游；业务服务由公共拦截器 `UserInfoInterceptor`（`lms.mvc.user-header-enabled=true` 开启）解析进 `UserContext`。

### 接口清单

| 方法 | 路径 | 说明 | 登录 |
|---|---|---|---|
| POST | /auth/register | 注册（`userType` 选 1 学生 / 2 教师；同步经 Feign 在 lms-user 建档案，失败本地事务回滚） | 否 |
| POST | /auth/login | 账号密码登录 → 返回 JWT + userId/userType | 否 |
| POST | /auth/logout | 登出（token 的 jti 进黑名单） | 是 |
| GET | /auth/me | 解析 token 返回基本信息 | 是 |
| GET | /users/me | 当前用户档案（含教师/学生扩展字段） | 是 |
| PUT | /users/me | 修改当前用户资料 | 是 |
| GET | /users/{id} | 按 id 查档案 | 是 |
| GET | /admin/users/page | 管理端分页（`userType`/`keyword` 筛选） | 是 |

### 配置中心（Nacos）

| dataId | 内容 |
|---|---|
| lms-common.yaml | Redis、`lms.jwt.secret`、`lms.jwt.ttl`、`user-info` 头名（各服务共享导入） |
| lms-auth.yaml | 端口 8085、数据源 lms_auth、springdoc 分组 |
| lms-user.yaml | 端口 8086、数据源 lms_user、springdoc 分组、`lms.mvc.user-header-enabled` |
| lms-gateway.yaml | 端口 8080、路由（lb://）、免登录白名单 |

修改 `nacos-config/` 后执行 `powershell -ExecutionPolicy Bypass -File scripts/push-nacos-config.ps1` 重新发布。

### 快速验证

```bash
# 1) 基础设施 + 建库（脚本在 docker/mysql/init/，已建好可跳过）
docker compose up -d
# 2) 发布配置到 Nacos
powershell -ExecutionPolicy Bypass -File scripts/push-nacos-config.ps1
# 3) 启动服务（顺序：lms-user → lms-auth → lms-gateway）
mvn -pl lms-user -am spring-boot:run &   # 或 IDEA 直接跑各 Application
# 4) 注册学生 → 登录 → 经网关查档案
curl -X POST localhost:8080/auth/register -H "Content-Type: application/json" \
  -d '{"username":"stu001","password":"123456","userType":1,"nickname":"张三","studentNo":"2024001"}'
curl -X POST localhost:8080/auth/login -H "Content-Type: application/json" \
  -d '{"username":"stu001","password":"123456"}'   # 返回 token
curl localhost:8080/users/me -H "Authorization: Bearer <token>"
```

> 前端拿到登录响应中的 `userType` 即可分流：1 跳学生端、2 跳教师端；后续基于 `UserContext.getUserType()` 做角色鉴权。

## 配置说明

- **代码注释**：所有模块必须按 `docs/CODE_COMMENT_STYLE.md`（code-comment-style 业务注释风格）注释——实体/工具类重注释、服务层业务步骤编号注释、controller 轻注释、禁用 HTML 标签。提交前对照自查清单。
- 版本只在根 `pom.xml` 的 `properties` + `dependencyManagement` 管理一次，子模块不写版本。
- 每个业务模块按技能包约定：
  - 独立数据库 `lms_<domain>`（在 `docker/mysql/init/` 建库脚本或 Nacos 配置中创建）；
  - 包根 `com.lms.<domain>`，启动类 `<Domain>Application` 位于包根；
  - 标准目录：`controller / service / mapper / domain(dto|po|query|vo) / config / constants / enums / mq / task ...`；
  - 通过 Nacos 注册与发现，网关加一条 `lb://<service-id>` 路由。
- 公共能力（统一响应 `R`、全局异常、分页 `PageQuery/PageDTO`、`UserContext`、字段自动填充、Knife4j）由 `lms-common` 自动配置提供，业务模块只需依赖它。
- AI 能力统一收敛在 `lms-ai`：基于 AgentScope Java v2（`io.agentscope:agentscope-core` + `agentscope-openai-spring-boot-starter`，2.0.2），LLM 走通义千问 DashScope 的 OpenAI 兼容模式。启动前设置环境变量 `DASHSCOPE_API_KEY`（阿里云百炼控制台获取），或在 Nacos 覆盖 `agentscope.openai.*` 配置。最小跑通接口：`POST http://localhost:8090/ai/chat`（body：`{"prompt":"你好"}`，经网关 `http://localhost:8080/ai/chat`）。
- 参考文档：AgentScope Java 官方文档 https://java.agentscope.io/v2/zh/ ；DashScope 接入 https://java.agentscope.io/v2/en/integration/model/dashscope.html ；Nacos 官方扩展（AgentScope ↔ Spring Cloud Alibaba）https://github.com/nacos-group/agentscope-extensions-nacos

## Maven 常用命令

```bash
mvn clean install          # 全量构建
mvn -pl lms-common install # 只构建公共库
mvn -pl lms-gateway -am spring-boot:run  # 构建依赖并启动网关
```
