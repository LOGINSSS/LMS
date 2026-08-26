# lms-course 课程服务

> 课程卡片分页展示、教师建课与上下架、学生选课退课、课程统计（选课量 / 今日 / Top，供统计服务 Feign 聚合）。

## 职责定位

- 分层：**业务层**
- 依赖：lms-common（用户上下文）、lms-user（Feign 取教师昵称）
- 协作：lms-search 同步课程数据到 ES；lms-statistics 聚合选课统计；lms-learning 课次挂课程

## 运行信息

| 项 | 值 |
|---|---|
| 端口 | 8087 |
| 库 | `lms_course`（`course` / `course_enrollment`） |
| Nacos 配置 | `nacos-config/lms-course.yaml` |
| 网关路由 | `/courses/**`、`/admin/courses/**` |

## 核心接口

### 学生 / 公开

| 方法 | 路径 | 说明 | 鉴权 |
|---|---|---|---|
| GET | `/courses/page` | 课程卡片分页（仅上架中） | 登录 |
| GET | `/courses/{id}` | 课程详情 | 登录 |
| POST | `/courses/{id}/enroll` | 选课（幂等防重复） | 学生 |
| POST | `/courses/{id}/quit` | 退课 | 学生 |
| GET | `/courses/enrolled` | 我的课程列表 | 登录 |

### 教师（Admin）

| 方法 | 路径 | 说明 | 鉴权 |
|---|---|---|---|
| POST | `/admin/courses` | 创建课程 | 教师 |
| PUT | `/admin/courses/{id}` | 编辑课程 | 教师 |
| PUT | `/admin/courses/{id}/status` | 上架 / 下架（CourseStatus） | 教师 |
| GET | `/admin/courses/mine` | 我创建的课程 | 教师 |

### 统计（供 lms-statistics Feign 调用）

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/admin/courses/stats/enroll-total` | 选课总人次 |
| GET | `/admin/courses/stats/today` | 今日选课人次 |
| GET | `/admin/courses/stats/top` | 热门课程 Top（按选课量） |

## 数据模型

| 表 | 说明 |
|---|---|
| `course` | 课程：name / category / cover / intro / teacher_id / status（草稿 / 上架 / 下架） |
| `course_enrollment` | 选课：course_id / user_id / status；唯一键防重复选课 |

> 业务规则：一个课程一个教师，可接受多个学生；退课后可再次选课（status 流转）。

## 跨服务协作

- `UserClient`（Feign）→ lms-user 批量查询教师昵称（`CourseCardVO` / `CourseTopVO` 装配用）
- 统计接口 `/admin/courses/stats/**` → 被 lms-statistics 经 Feign 聚合
- 课程数据 → 被 lms-search `POST /search/sync` 拉取同步进 ES

## 目录结构

```
com/lms/course/
├── CourseApplication.java
└── course/
    ├── client/        # UserClient + FallbackFactory + dto（UserSimpleDTO）
    ├── constants/     # CourseErrorInfo
    ├── controller/    # CourseController / AdminCourseController
    ├── domain/        # dto（CourseCardVO/CourseFormDTO）、po（Course/CourseEnrollment）、query、vo
    ├── enums/         # CourseStatus / EnrollmentStatus
    ├── mapper/        # CourseMapper / CourseEnrollmentMapper
    └── service/       # ICourseService + CourseServiceImpl
```
