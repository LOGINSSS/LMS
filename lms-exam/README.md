# lms-exam 题库服务

> 题目管理（单选 / 多选 / 判断）与题库业务绑定：题目可挂到任意业务（如课程 / 考试）下并配置分值，支撑前端题库管理页。

## 职责定位

- 分层：**业务层**
- 依赖：lms-common（用户上下文，识别建题教师）
- 协作：后续考试编排可引用 `question_biz`；前端「题库管理」页面对接

## 运行信息

| 项 | 值 |
|---|---|
| 端口 | 8092 |
| 库 | `lms_exam`（`question` / `question_biz`） |
| Nacos 配置 | `nacos-config/lms-exam.yaml` |
| 网关路由 | `/admin/questions/**`、`/questions/**` |

## 核心接口

### 教师（Admin）

| 方法 | 路径 | 说明 | 鉴权 |
|---|---|---|---|
| POST | `/admin/questions` | 创建题目（单选 / 多选 / 判断） | 教师 |
| PUT | `/admin/questions/{id}` | 编辑题目 | 教师 |
| DELETE | `/admin/questions/{id}` | 删除题目 | 教师 |
| GET | `/admin/questions/{id}` | 题目详情 | 教师 |
| GET | `/admin/questions/page` | 题目分页（按类型 / 分类 / 难度筛选） | 教师 |
| POST | `/admin/questions/{id}/biz` | 绑定 / 解绑业务（bizId + score） | 教师 |

### 学生 / 通用

| 方法 | 路径 | 说明 | 鉴权 |
|---|---|---|---|
| GET | `/questions/biz/{bizId}` | 某业务下的题目列表（练习 / 考试用） | 登录 |

## 数据模型

| 表 | 说明 |
|---|---|
| `question` | name（题干）/ type（1 单选 / 2 多选 / 3 判断）/ category / difficulty（1 易 / 2 中 / 3 难）/ analysis（解析）/ answer（答案 JSON）/ status |
| `question_biz` | question_id / biz_id（业务 id，如课程 / 考试 id）/ score（该业务下分值）；唯一键 `uk_question_biz` |

## 目录结构

```
com/lms/exam/
├── ExamApplication.java
└── exam/
    ├── constants/     # QuestionErrorInfo
    ├── controller/    # AdminQuestionController / QuestionController
    ├── domain/        # dto（QuestionFormDTO）、po（Question/QuestionBiz）、query（QuestionPageQuery）、vo
    ├── enums/         # Difficulty / QuestionType
    ├── mapper/        # QuestionMapper / QuestionBizMapper
    └── service/       # IQuestionService + QuestionServiceImpl
```
