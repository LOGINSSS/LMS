# lms-gateway 网关

> 所有流量的统一入口：路由转发（`lb://` 负载均衡）、JWT 统一鉴权、白名单放行、CORS 跨域、把 `userId/userType` 写入 `user-info` 头透传给下游业务服务。

## 职责定位

- 分层：**接入层**
- 依赖：Nacos（服务发现 + 配置中心）、Redis（登出黑名单）、lms-common（JWT 工具）
- 技术栈：Spring Cloud Gateway（WebFlux 响应式）、ReactiveStringRedisTemplate

## 运行信息

| 项 | 值 |
|---|---|
| 端口 | 8080 |
| Nacos 配置 | `lms-gateway.yaml`（路由 / CORS / 白名单）、`lms-common.yaml`（JWT 密钥 / Redis） |
| 数据存储 | Redis（JWT 登出黑名单，key：`jwt:blacklist:<jti>`） |

## 鉴权流程（AuthGlobalFilter，Order = HIGHEST_PRECEDENCE）

1. **OPTIONS 预检**直接放行（浏览器跨域先发预检）
2. **白名单放行**：`/auth/login`、`/auth/register`、`/doc.html`、`/webjars/**`、`/v3/api-docs/**`、`/swagger-ui/**`、`/favicon.ico`、`/actuator/**`
3. **校验 JWT**：验签 + 有效期（hutool JWT，密钥来自 Nacos `lms-common.yaml`）
4. **查登出黑名单**：命中说明该 token 已主动登出，即使未过期也拒绝
5. **透传用户信息**：写入 `user-info` 头 `{"userId":..,"userType":..}` 给下游业务服务

## 路由表（lms-gateway.yaml）

| 请求前缀 | 目标服务 |
|---|---|
| `/auth/**` | lms-auth |
| `/users/**`、`/admin/users/**` | lms-user |
| `/courses/**`、`/admin/courses/**` | lms-course |
| `/medias/**` | lms-media |
| `/likes/**` | lms-remark |
| `/search/**`、`/interests/**` | lms-search |
| `/admin/questions/**`、`/questions/**` | lms-exam |
| `/lessons/**`、`/admin/lessons/**`、`/learn/**`、`/notes/**`、`/qa/**`、`/sign-in`、`/points/**` | lms-learning |
| `/dashboard/**` | lms-statistics |
| `/ai/**` | lms-ai |

> 说明：问答接口挂在 `/qa/**` 前缀下，规避与 exam 模块 `/questions/**` 的路由冲突。

## 目录结构

```
src/main/java/com/lms/gateway/
├── GatewayApplication.java
└── filter/
    ├── AuthGlobalFilter.java        # 全局鉴权过滤器（核心）
    ├── GatewayAuthProperties.java   # 白名单配置绑定（lms.gateway.whitelist）
    └── GatewayConstants.java        # 常量（头名 / token 前缀 / 错误响应体）
```
