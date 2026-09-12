# Implementation Plan: runtime-integration

## Overview

Implement the approved `runtime-integration` specification as the first behavior-preserving `lms-ai` restructuring slice. Introduce LMS-owned invocation contracts, adapt the existing AgentScope execution path behind them, migrate the main conversation service, and verify that authorization and chat orchestration behavior remain compatible.

## Architecture Decisions

- Application code owns invocation inputs and outputs; infrastructure owns AgentScope message and runtime types.
- The existing `PersonalAgentFactory` remains temporarily reusable by the infrastructure adapter so this slice stays small.
- The synchronous contract preserves current behavior. Native token streaming and cancellation are a later additive contract.
- Existing public HTTP, SSE, configuration, Redis, and database contracts do not change.

## Dependency Graph

```text
LMS-owned invocation contracts
    -> AgentScope runtime adapter
        -> AgentChatService migration
            -> focused tests and full lms-ai verification
```

## Task List

### Phase 1: Runtime contract

- Task 1: Add application-owned conversation and invocation records plus `AgentRuntime`.
- Task 2: Implement the AgentScope adapter and verify message/context/result mapping.

### Checkpoint: Runtime boundary

- Application contracts contain no `io.agentscope.*` types.
- Focused runtime tests pass.
- `lms-ai` compiles.

### Phase 2: Conversation migration

- Task 3: Migrate `AgentChatService` to `AgentRuntime` without changing orchestration behavior.
- Task 4: Add a focused service test for the migrated conversation path.

### Checkpoint: Behavior compatibility

- Existing authorization tests pass.
- New runtime and conversation tests pass.
- `lms-ai` build passes.

### Phase 3: Review and module assessment

- Task 5: Review the diff for dependency leaks, security regressions, and accidental scope expansion.
- Task 6: Produce a code-backed `lms-ai` feature inventory, verification report, and Harness completeness assessment.

## Risks and Mitigations

| Risk | Impact | Mitigation |
|---|---|---|
| Dirty worktree overlaps `lms-ai` | High | Touch only named files; never revert existing changes; inspect scoped diffs |
| Runtime abstraction drops Context fields | High | Adapter test covers identity, Session, intent hint, and message mapping |
| Agent lifecycle leak | Medium | Adapter owns `close()` in `finally` and tests the lifecycle |
| Exception semantics change | Medium | Preserve the current application-facing failure message |
| Premature streaming abstraction | Medium | Keep the first port synchronous; specify streaming separately |

## Open Questions

None. The user authorized implementation on 2026-09-11.
