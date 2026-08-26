# lms-auth 认证服务

> 注册 / 登录 / 登出 / 当前用户信息：账号与登录日志入库，签发 JWT；注册时经 Feign 在 lms-user 同步创建用户档案，本地事务失败整体回滚。

## 职责定位

- 分层：**接入层（认证）**
- 依赖：lms-user（Feign 创建档案）、Redis（登出黑名单 / jti）、MySQL `lms_auth`
- 技术栈：Spring MVC + MyBatis-Plus + hutool JWT + OpenFeign

## 运行信息

| 项 | 值 |
|---|---|
| 端口 | 8085 |
| 库 | `lms_auth`（`account` / `login_log`） |
| Nacos 配置 | `nacos-config/lms-auth.yaml`（数据源 / SQL 日志 / springdoc） |
| 网关路由 | `/auth/**`（`/auth/login`、`/auth/register` 白名单免鉴权） |

## 核心接口

| 方法 | 路径 | 说明 | 鉴权 |
|---|---|---|---|
| POST | `/auth/register` | 注册（userType 1 学生 / 2 教师），同步经 Feign 建档案，失败回滚 | 白名单 |
| POST | `/auth/login` | 账号密码登录 → 返回 JWT + userId / userType | 白名单 |
| POST | `/auth/logout` | 登出（token 的 jti 写入 Redis 黑名单） | 登录 |
| GET | `/auth/me` | 解析 token 返回基本信息 | 登录 |

## 数据模型

| 表 | 说明 |
|---|---|
| `account` | 账号：手机号、密码（BCrypt）、user_type（1 学生 / 2 教师）、status；密码不落库明文 |
| `login_log` | 登录日志：user_id、登录时间、来源 |

## 跨服务协作

- `UserClient`（Feign）→ lms-user `POST /users` 创建用户档案（幂等可重试）
- `UserClientFallbackFactory` 降级兜底；本地事务内调用失败整体回滚，保证「注册成功必有档案」

## 目录结构

```
com/lms/auth/
├── AuthApplication.java
└── auth/
    ├── client/        # UserClient + FallbackFactory + dto（UserProfileDTO）
    ├── constants/     # AuthErrorInfo / RedisConstants（黑名单 key 前缀）
    ├── controller/    # AuthController
    ├── domain/        # dto（Login/Register/LoginVO/UserInfoVO）、po（Account/LoginLog）
    ├── enums/         # UserStatus
    ├── mapper/        # AccountMapper / LoginLogMapper
    └── service/       # IAuthService + AuthServiceImpl
```
