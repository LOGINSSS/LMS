# lms-learning 学习过程服务

> 课次、学习记录（进度上报）、笔记、互动问答、签到、积分（行为奖励）与积分榜。

## 职责定位

- 分层：**业务层**（学习域）
- 依赖：lms-course（课次挂课程）、lms-media（课次视频）、lms-remark（笔记 / 问答点赞，bizType=2 / 3）

## 运行信息

| 项 | 值 |
|---|---|
| 端口 | 8093 |
| 库 | `lms_learning`（7 张表） |
| Nacos 配置 | `nacos-config/lms-learning.yaml` |
| 网关路由 | `/lessons/**`、`/admin/lessons/**`、`/learn/**`、`/notes/**`、`/qa/**`、`/sign-in`、`/points/**` |

## 核心接口

### 课次与学习记录

| 方法 | 路径 | 说明 | 鉴权 |
|---|---|---|---|
| GET | `/lessons?courseId=` | 课次列表 | 登录 |
| GET | `/lessons/{id}` | 课次详情（LessonVO） | 登录 |
| POST | `/admin/lessons` | 创建课次（挂课程 + 媒资视频） | 教师 |
| POST | `/learn/records` | 上报学习进度（幂等合并） | 登录 |
| GET | `/learn/progress` | 我的课程进度 | 登录 |

### 笔记

| 方法 | 路径 | 说明 | 鉴权 |
|---|---|---|---|
| POST | `/notes` | 写笔记 | 登录 |
| GET | `/notes/page?courseId=` | 按课程查笔记分页（NotePageQuery） | 登录 |
| PUT | `/notes/{id}` | 编辑笔记（仅本人） | 登录 |
| DELETE | `/notes/{id}` | 删除笔记（仅本人） | 登录 |

### 互动问答（/qa 前缀，规避 exam 的 /questions 路由）

| 方法 | 路径 | 说明 | 鉴权 |
|---|---|---|---|
| POST | `/qa/questions` | 提问 | 登录 |
| GET | `/qa/questions/page?courseId=` | 问题列表（含回答，QaPageQuery） | 登录 |
| POST | `/qa/questions/{id}/answers` | 回答问题 | 登录 |

### 签到与积分

| 方法 | 路径 | 说明 | 鉴权 |
|---|---|---|---|
| POST | `/sign-in` | 签到（一天一次，奖励积分） | 登录 |
| GET | `/points/records` | 我的积分明细 | 登录 |
| GET | `/points/board?size=` | 积分榜 Top N（PointsBoardVO） | 登录 |
| GET | `/points/stats/sign-today` | 今日签到数（统计服务 Feign 用） | 内部 |
| GET | `/points/stats/learn-today` | 今日学习人次（统计服务 Feign 用） | 内部 |
| GET | `/points/stats/learn-total` | 累计学习人次（统计服务 Feign 用） | 内部 |

## 数据模型

| 表 | 说明 |
|---|---|
| `lesson` | course_id / name（课次名）/ media_id（关联媒资视频）/ sort |
| `learning_record` | user_id / course_id / lesson_id / progress（进度 %）/ last_learn_time；唯一键 `uk_user_lesson` |
| `note` | user_id / course_id / lesson_id / content |
| `question`（问答） | user_id / course_id / title / content |
| `answer` | question_id / user_id / content / accepted（是否采纳） |
| `points_record` | user_id / type（1 签到 / 2 学习 / 3 提问 / 4 回答采纳 / 5 点赞...）/ points（增减分） |
| `sign_in` | user_id / sign_date；唯一键 `uk_user_date` |

> 积分榜不建表：按 `points_record` 实时聚合（SUM）。行为奖励：签到 / 学习 / 提问 / 回答采纳 / 点赞等写入积分流水。

## 目录结构

```
com/lms/learning/
├── LearningApplication.java
└── learning/
    ├── constants/     # LearningErrorInfo
    ├── controller/    # LessonController / NoteController / QaController / PointsController
    ├── domain/        # dto / po（7 张表）/ query / vo
    ├── enums/         # PointsType
    ├── mapper/        # Lesson / LearningRecord / Note / QaQuestion / Answer / PointsRecord / SignIn
    └── service/       # ILearningService + LearningServiceImpl
```
