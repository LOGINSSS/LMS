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
├── docker/mysql/init/       # MySQL 首次启动自动执行的建库脚本
├── lms-common/              # 公共库（library）：统一响应/异常、分页、hutool 工具、自动配置
├── lms-gateway/             # 网关：路由（lb://）、负载均衡
├── lms-ai/                  # AI 能力服务：AgentScope Java + DashScope（LLM 接入）
└── ...业务模块（lms-user / lms-course / ...）后续逐模块添加
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
| MySQL | localhost:3306 | root / root（默认库 `lms`） |
| Redis | localhost:6379 | 密码 `123456` |
| Kafka（宿主机应用连接） | localhost:29092 | — |
| Kafka（容器内服务连接） | lms-kafka:9092 | — |
| Elasticsearch | http://localhost:9200 | 已关闭安全认证 |
| kafka-ui（可选） | http://localhost:9000 | — |
| Kibana（可选） | http://localhost:5601 | — |
| 网关 | http://localhost:8080 | — |
| AI 服务（lms-ai） | http://localhost:8090 | 需配置 DashScope API Key |

### 3. 构建与启动应用

```bash
mvn clean install              # 构建全部模块（common → gateway → 业务模块）
mvn -pl lms-gateway spring-boot:run          # 单独启动网关（先保证 Nacos 已 up）
# IDEA 中直接运行 GatewayApplication，profile 选 dev
```

> 启动顺序：先 `docker compose up -d`（等 healthy）→ 再启动网关/业务服务。

## 配置说明

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
