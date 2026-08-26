# lms-ai AI 能力服务

> AgentScope Java + 阿里云百炼 DashScope（OpenAI 兼容模式）的 LLM 对话服务，前端「AI 对话」入口。

## 职责定位

- 分层：**业务层**（AI 能力）
- 依赖：lms-common；配置在本地 `application.yml`（Nacos 中暂无 `lms-ai.yaml`，import 为 optional 不影响启动）

## 运行信息

| 项 | 值 |
|---|---|
| 端口 | 8090（本地 application.yml） |
| 库 | 无（无状态） |
| Nacos 配置 | 无（本地配置兜底） |
| 网关路由 | `/ai/**` |

> ⚠️ 注意：当前本地端口 8090 与 lms-remark（Nacos 配置 8090）冲突，两者勿同时启动；建议后续把 lms-ai 端口调整到 8095 或修改 lms-remark 配置。

## 核心接口

| 方法 | 路径 | 说明 | 鉴权 |
|---|---|---|---|
| POST | `/ai/chat` | LLM 对话（流式输出） | 登录 |

## 配置要点

- `api-key` 通过环境变量 `DASHSCOPE_API_KEY` 注入（不要写死在代码 / 配置文件里）
- `base-url`：`https://dashscope.aliyuncs.com/compatible-mode/v1`
- `model-name`：`qwen-plus`（可换 `qwen-max` / `qwen-turbo`）
- `stream: true`：流式输出

```bash
# 启动前设置环境变量
set DASHSCOPE_API_KEY=sk-xxxx
```

## 目录结构

```
com/lms/ai/
├── AiApplication.java
├── controller/    # ChatController
└── service/       # ChatService（AgentScope 调用封装）
```
