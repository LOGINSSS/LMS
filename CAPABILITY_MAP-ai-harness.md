# Capability Map: LMS AI Harness

## Objective

Restructure `lms-ai` so that its source tree expresses the runtime architecture before new capabilities are enabled. Existing HTTP URLs, database data, configuration behavior, and verified user flows remain compatible during the restructuring.

## Modules

| Module id | Responsibility | Depends on |
|---|---|---|
| `entry-api` | HTTP/SSE entry points for Agent, Session, Profile, Task, and HITL operations | `application-core` |
| `application-core` | Conversation use cases and deterministic multi-step coordination | `context-memory`, `agent-definition`, `harness-governance`, `runtime-integration` |
| `intent-routing` | Deterministic intent recognition, optional LLM classification, and routing decisions | `runtime-integration` |
| `context-memory` | Sessions, messages, context assembly, profiles, and long-term-memory contracts | `reme-personal-wiki`, `persistence-integration` |
| `agent-definition` | Declarations and registration rules for two personal Agents and fifteen domain/process Agents | `capability-tools`, `runtime-integration` |
| `capability-tools` | Course, exam, learning, knowledge, task, and other business capabilities exposed as Agent tools | `service-integration` |
| `harness-governance` | Role matrix, tool switches, budgets, depth limits, HITL, and memory policy | `observability` |
| `observability` | Trace, task tree, SSE lifecycle, error identifiers, duration, and summaries | `persistence-integration` |
| `runtime-integration` | AgentScope, DashScope, model invocation, and future native streaming | — |
| `service-integration` | Feign, Python RAG, IM, and business-microservice adapters | — |
| `persistence-integration` | MySQL, Redis, Mapper, and storage implementations | — |
| `reme-personal-wiki` | Latest ReMe, private personal Wiki, BM25, WikiLink, and memory lifecycle | `harness-governance` |
| `task-events` | Kafka task notifications and asynchronous task lifecycle | `observability` |
| `multimodal-input` | Image input, multimodal model messages, and media/RAG cooperation | `entry-api`, `runtime-integration`, `capability-tools` |

## Dependency Direction

```text
Entry adapters
    -> Application use cases
        -> Domain and Harness contracts
            <- Infrastructure adapters
```

Application code may depend on an internal port such as `LongTermMemoryPort` or `AgentRuntime`, but must not directly depend on ReMe HTTP clients, MyBatis mappers, Feign clients, Redis templates, or vendor response objects.

## Build Order

```text
Target package contracts
    -> Agent / Capability / Harness placement
    -> Session / Memory / ContextAssembler separation
    -> Application use-case consolidation
    -> API entry-point separation
    -> Infrastructure adapter placement
    -> ReMe personal Wiki
    -> optional LLM intent classification
    -> native model streaming and cancellation
    -> Kafka task notifications
    -> direct multimodal image input
```

## Implemented Physical Slices

```text
application/
├── context/
│   ├── ContextAssembler.java
│   ├── ContextAssemblyRequest.java
│   ├── ContextCompressor.java
│   └── ConversationContext.java
├── memory/
│   ├── PersonalWikiService.java
│   └── SessionClosureService.java
└── port/out/
    ├── AgentRuntime.java
    ├── LongTermMemoryPort.java
    ├── PersonalWikiPort.java
    └── SessionMemoryPort.java

infrastructure/
├── ai/agentscope/AgentScopeRuntimeAdapter.java
└── memory/
    ├── ProfileMemoryAdapter.java
    ├── ReMeMemoryAdapter.java
    ├── ReMeSessionMemoryAdapter.java
    └── ReMeHttpJobClient.java
```

`ContextAssembler` owns per-turn assembly. `AgentSessionService` remains the short-term source and
`LongTermMemoryPort` is the recall source. `ReMeMemoryAdapter` composes ReMe Wiki recall with the existing
MySQL profile, while `PersonalWikiPort` owns explicit write/correct/delete commands. ReMe response types and
HTTP details remain in Infrastructure. The old deprecated AgentScope Java LTM extension has been removed.
At session close, Application snapshots the bounded source transcript and atomically persists a MySQL Outbox row
with the closed session state. Redis cleanup runs only after commit. A bounded scheduler delivers `auto_memory` with
processing leases, exponential retry and a dead-letter state; ReMe failures cannot roll back session closure.

## Approved Decisions

- The source tree is restructured before incomplete capabilities are enabled.
- System-level JWT authentication remains in Gateway/Common; `lms-ai` exposes an entry layer and performs resource-side authorization.
- Agent execution and Session lifecycle are separate API responsibilities.
- Context is the per-invocation assembly; Session and Memory are input sources, not synonyms for Context.
- The latest ReMe architecture is the target. No legacy ReMe data or API compatibility is required.
- Personal Wiki retrieval initially uses file-native memory, BM25, and WikiLink expansion. Embedding retrieval is optional and evaluation-driven.
- Course RAG keeps its independent, course-isolated Milvus retrieval path.
- A teacher cannot directly read a student's private personal Wiki. Only explicitly shared learning summaries may cross that boundary.
- Existing HTTP URLs, persisted data, configuration behavior, and verified flows remain compatible during restructuring.
- Disabled or incomplete capabilities remain on the roadmap until explicitly removed by the user.

## Review Status

Approved by the user on 2026-09-11.
