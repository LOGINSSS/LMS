# lms-remark 评价互动服务

> 跨业务对象（课程 / 笔记 / 问答）的通用点赞互动：记录 `liked_record`，支持点赞切换、计数、单条 / 批量已赞状态查询。

## 职责定位

- 分层：**业务层**
- 依赖：lms-common（用户上下文，识别点赞人）
- 协作：被课程（bizType=1）、笔记（bizType=2）、问答（bizType=3）等模块复用

## 运行信息

| 项 | 值 |
|---|---|
| 端口 | 8090 |
| 库 | `lms_remark`（`liked_record`） |
| Nacos 配置 | `nacos-config/lms-remark.yaml` |
| 网关路由 | `/likes/**` |

## 核心接口

| 方法 | 路径 | 说明 | 鉴权 |
|---|---|---|---|
| POST | `/likes/{bizType}/{bizId}` | 切换点赞状态，返回 {liked, likeCount} | 登录 |
| GET | `/likes/count/{bizType}/{bizId}` | 点赞数 | 登录 |
| GET | `/likes/status/{bizType}/{bizId}` | 当前用户是否已赞 | 登录 |
| GET | `/likes/statuses/{bizType}?bizIds=1,2,3` | 批量已赞状态（列表页用） | 登录 |

## 数据模型

| 表 | 说明 |
|---|---|
| `liked_record` | user_id（点赞人）/ biz_type（1 课程 / 2 笔记 / 3 问答）/ biz_id（对象 id）/ status（1 已赞 / 0 取消）；唯一键 `uk_user_biz(user_id, biz_type, biz_id)` |

> 设计要点：不按业务建独立点赞表，一份记录 + biz_type 通用复用；点赞数为实时 count，取消点赞为软状态切换。

## 目录结构

```
com/lms/remark/
├── RemarkApplication.java
└── remark/
    ├── constants/     # LikeErrorInfo
    ├── controller/    # LikeController
    ├── domain/        # po（LikedRecord）、vo（LikeStatusVO）
    ├── enums/         # BizType
    ├── mapper/        # LikedRecordMapper
    └── service/       # ILikeService + LikeServiceImpl
```
