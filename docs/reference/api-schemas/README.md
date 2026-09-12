# 前后端接口契约（JSON Schema）

本目录沉淀 LMS 前后端接口的 **JSON Schema 契约定义**，与后端 Java DTO 一一对应，是前端 `lms-web/src/api/` 与后端 Controller 出参/入参的权威描述。

## 为什么写 JSON Schema

- **契约先行**：接口结构（字段、类型、必填、取值范围）集中定义，前后端各改各的代码但必须对齐这份契约
- **机器可读**：可被 IDE 插件 / OpenAPI 工具 / 校验库（ajv 等）消费，也便于 AI 工具链生成前后端代码
- **文档即代码**：与 `docs/BUSINESS_MODULES.md` 互补——蓝图讲业务设计，这里的 schema 讲数据形状

## 文件结构

| 文件 | 内容（$defs） | 对应后端 |
|---|---|---|
| `R.schema.json` | 统一响应包装 `R`（code/msg/data） | lms-common `R.java` |
| `PageDTO.schema.json` | 分页负载 `PageDTO`（total/list） | lms-common `PageDTO.java` |
| `auth.schema.json` | `LoginFormDTO` / `RegisterFormDTO` / `LoginVO` | lms-auth |
| `user.schema.json` | `UserDetailVO`（人物画像）/ `UserProfileFormDTO` | lms-user |
| `course.schema.json` | `CourseCardVO` / `CourseFormDTO` | lms-course |
| `dashboard.schema.json` | `DashboardVO` / `TodayStatsVO` | lms-statistics |
| `learning.schema.json` | `PointsVO` / `PointsBoardVO` | lms-learning |
| `ai.schema.json` | `ChatRequest` / `ChatResponse` | lms-ai |
| `ai-tools.schema.json` | **AI 工具面契约**：`RagChatRequest` / `QuestionForm` / `PaperForm` / `ExamForm` / `HomeworkForm` / `AnswerSubmit` / `ExamMineItem` 等 | lms-ai tools/ + client/ |
| `ai-output.schema.json` | **AI 输出产物契约**：`AiEnvelope`（校验信封）+ `ValidationInfo` + `DiagnoseReport` / `PlanPath` / `ExercisePack` / `AssessReport` / `GeneratedQuestion` / `GeneratedPaper` / `GeneratedHomeworkQuestion` / `JudgementResult` / `ImageQuestionExtract` / `RagAnswer` / `ProfileSummary` / `ImageDescription` / `ChatReply` | lms-ai 管道（LearningPipelineService + 0.3 question-gen/paper-gen/judge）+ lms-kb（RAG/图片）+ 对话/画像摘要 |

## AI 数据契约约定（重要）

**所有与 AI 相关的数据提供（工具入参/出参）必须符合 `ai-tools.schema.json`；AI 返回的结构化内容（管道产物、判卷报告、出题/出卷 JSON）必须符合 `ai-output.schema.json`。** 规则：

1. **工具面**（喂给 Agent 的数据）：`@Tool` 方法与 Feign 契约的入参出参，一律 `additionalProperties:false` 严格校验；
2. **AI 输出必过校验**：每个 AI 返回点都必须经 schema 校验（`AiEnvelope.validation`）；**校验不通过 → 加标记（needs_review/failed）并要求 LLM 携带 errors 重新生成**（重试策略见 V0.3演进Spec §5.4），禁止静默放行；
3. **契约即实现**：prompt 中内嵌的 JSON 示例与解析端（`LearningPipelineService.extractGraded`、0.3 `AiOutputValidator`）以 `ai-output.schema.json` 为准——改 prompt 示例必须同步改 schema；
4. **附件一律 URL**：题目/作业题中的图片等附件存 `{url,name,type}`，本体走 lms-media，**禁止 base64 入库**（见 V0.3演进Spec §2.2）；
5. 0.3 新增的 AI 功能（出题/出卷/判卷/图片供题/作业）按本目录契约先行——先定义 schema，再写管道与接口。

## 引用方式

所有接口响应最外层都是 `R`，分页接口的 `data` 是 `PageDTO`。示例（课程分页）：

```jsonc
{
  // R 包装
  "code": 1,
  "msg": "OK",
  "data": {
    // PageDTO 负载
    "total": 42,
    "list": [
      { /* CourseCardVO，见 course.schema.json#/$defs/CourseCardVO */ }
    ]
  }
}
```

在其它 schema 中通过 `$ref` 复用：

```jsonc
{ "$ref": "R.schema.json" }                    // 取整个包装
{ "$ref": "PageDTO.schema.json" }              // 取分页负载
{ "$ref": "course.schema.json#/$defs/CourseCardVO" }  // 取具体模型
```

## 维护约定

- 后端新增/修改 DTO 字段时，同步更新对应 schema 文件（`additionalProperties: false` 保证契约严格）
- 前端新增接口时，先在这里补契约，再在 `lms-web/src/api/` 写调用
- 字段说明沿用后端 Javadoc 的 `@Schema(description=...)` 文案，保持两端一致
