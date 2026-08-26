# lms-search 搜索服务

> Elasticsearch 课程搜索（关键词 / 分类）、按兴趣标签的课程推荐、兴趣标签上报与查询、ES 索引同步。

## 职责定位

- 分层：**业务层**
- 依赖：ES 8（索引 `course`）、MySQL `lms_search`（兴趣标签持久化）、lms-course（Feign 取课程数据同步索引）

## 运行信息

| 项 | 值 |
|---|---|
| 端口 | 8091 |
| 存储 | ES 索引 `course` + MySQL `lms_search`（`user_interests`） |
| Nacos 配置 | `nacos-config/lms-search.yaml`（数据源 + `spring.elasticsearch.uris`） |
| 网关路由 | `/search/**`、`/interests/**` |

## 核心接口

| 方法 | 路径 | 说明 | 鉴权 |
|---|---|---|---|
| POST | `/search/sync` | 全量同步课程数据到 ES 索引（Feign 拉取 course 数据） | 登录 |
| GET | `/search/courses?keyword=&category=&pageNo=&pageSize=` | ES 搜索分页（CourseDocVO） | 登录 |
| GET | `/search/recommend?size=10` | 按兴趣标签推荐课程 | 登录 |
| GET | `/interests` | 我的兴趣标签（按权重降序） | 登录 |
| POST | `/interests/record` | 上报兴趣标签（浏览 / 选课时调用，权重累加） | 登录 |

## 数据模型

| 存储 | 说明 |
|---|---|
| `course`（ES 索引） | 文档：id / name / intro / category / teacherName / cover / createTime / score |
| `user_interests`（MySQL） | user_id / tag（如分类）/ weight（点击 / 选课累加）；唯一键 `uk_user_tag` |

## 跨服务协作

- `CourseClient`（Feign）→ lms-course 拉取课程列表用于 `POST /search/sync` 同步索引；`CourseClientFallbackFactory` 降级兜底

## 目录结构

```
com/lms/search/
├── SearchApplication.java
└── search/
    ├── client/        # CourseClient + FallbackFactory + dto（CourseCardDTO）
    ├── constants/     # SearchErrorInfo
    ├── controller/    # SearchController / InterestController
    ├── domain/        # dto（TagFormDTO）、es（CourseDoc）、po（UserInterest）、vo（CourseDocVO）
    ├── mapper/        # UserInterestMapper
    ├── repository/    # CourseDocRepository（ES Spring Data）
    └── service/       # ISearchService + SearchServiceImpl
```
