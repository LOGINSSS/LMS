# lms-user 用户服务

> 用户档案：公共字段（`user`）+ 学生（`student_info`）/ 教师（`teacher_info`）扩展表；提供当前用户详情、资料修改、管理端分页与今日新增统计。

## 职责定位

- 分层：**业务层**
- 依赖：lms-common（`UserInfoInterceptor` 解析网关 `user-info` 头 → `UserContext`）
- 协作：lms-auth 注册成功后经 Feign 调用 `POST /users` 创建档案

## 运行信息

| 项 | 值 |
|---|---|
| 端口 | 8086 |
| 库 | `lms_user`（`user` / `teacher_info` / `student_info`） |
| Nacos 配置 | `nacos-config/lms-user.yaml`（数据源 / `lms.mvc.user-header-enabled: true`） |
| 网关路由 | `/users/**`、`/admin/users/**` |

## 核心接口

| 方法 | 路径 | 说明 | 鉴权 |
|---|---|---|---|
| POST | `/users` | 创建用户档案（认证服务注册时 Feign 调用，幂等可重试） | 内部 |
| GET | `/users/me` | 当前登录用户详情（含教师 / 学生扩展字段） | 登录 |
| PUT | `/users/me` | 修改当前用户资料（仅更新非 null 字段） | 登录 |
| GET | `/users/{id}` | 按 id 查用户详情 | 登录 |
| GET | `/admin/users/page` | 管理端用户分页（UserPageQuery 筛选） | 登录 |
| GET | `/admin/users/stats/today` | 今日新增用户数（统计服务 Feign 用） | 登录 |

## 数据模型

| 表 | 说明 |
|---|---|
| `user` | 公共档案：手机号 / 昵称 / 头像 / user_type；**不含密码** |
| `teacher_info` | 教师扩展：职称 / 简介等 |
| `student_info` | 学生扩展：学号 / 学校等 |

> 设计要点：档案按 `user_type` 扩展教师 / 学生字段（领域模型分离），查询时按需装配。

## 目录结构

```
com/lms/user/
├── UserApplication.java
└── user/
    ├── constants/     # UserErrorInfo
    ├── controller/    # UserController（档案）/ AdminUserController（管理端）
    ├── domain/        # dto（UserVO/UserDetailVO/UserProfileFormDTO）、po、query（UserPageQuery）
    ├── mapper/        # UserMapper / TeacherInfoMapper / StudentInfoMapper
    └── service/       # IUserService + UserServiceImpl
```
