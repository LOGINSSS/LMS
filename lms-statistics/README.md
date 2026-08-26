# lms-statistics 数据中心

> 数据看板：总览、今日数据、热门课程 Top10、积分榜 Top10；跨服务 Feign 聚合 + 每日快照表。

## 职责定位

- 分层：**业务层**（数据域）
- 依赖：lms-user / lms-course / lms-learning（Feign 聚合）+ MySQL `lms_statistics`

## 运行信息

| 项 | 值 |
|---|---|
| 端口 | 8094 |
| 库 | `lms_statistics`（`daily_stats`） |
| Nacos 配置 | `nacos-config/lms-statistics.yaml` |
| 网关路由 | `/dashboard/**` |

## 核心接口

| 方法 | 路径 | 说明 | 鉴权 |
|---|---|---|---|
| GET | `/dashboard` | 数据看板总览（用户数 / 课程数 / 选课数 / 笔记数等，DashboardVO） | 登录 |
| GET | `/dashboard/today` | 今日数据（新增用户 / 课程 / 学习人次 / 签到数，TodayStatsVO） | 登录 |
| GET | `/dashboard/top/courses?size=10` | 热门课程 Top10（按选课 / 学习量） | 登录 |
| GET | `/dashboard/top/points?size=10` | 积分榜 Top10（学习域聚合） | 登录 |

## 数据模型

| 表 | 说明 |
|---|---|
| `daily_stats` | stat_date（统计日期）/ user_count（新增用户）/ course_count（新增课程）/ learn_count（学习人次）/ enroll_count（选课人次）；唯一键 `uk_date` |

## 跨服务协作（Feign 聚合 + 降级兜底）

| Client | 目标服务 | 聚合数据 |
|---|---|---|
| `UserStatsClient` | lms-user | 今日新增用户数 |
| `CourseStatsClient` | lms-course | 选课统计 / 热门课程 Top |
| `LearningStatsClient` | lms-learning | 学习人次 / 签到 / 积分榜 |

> 每个 Client 均配 `FallbackFactory` 降级兜底：单个业务服务故障不影响看板渲染（返回兜底值并标记）。

## 目录结构

```
com/lms/statistics/
├── StatisticsApplication.java
└── statistics/
    ├── client/        # UserStatsClient / CourseStatsClient / LearningStatsClient + FallbackFactory + dto
    ├── constants/     # StatisticsErrorInfo
    ├── controller/    # StatisticsController
    ├── domain/        # po（DailyStats）、vo（DashboardVO / TodayStatsVO）
    ├── mapper/        # DailyStatsMapper
    └── service/       # IStatisticsService + StatisticsServiceImpl
```
