# Spec: runtime-integration

## Objective

Introduce a stable application-facing Agent runtime boundary and move AgentScope/DashScope-specific execution details behind infrastructure adapters. This is the first small restructuring slice: behavior remains unchanged, while application code stops constructing and invoking vendor runtime types directly.

The immediate user-visible behavior is intentionally unchanged. This boundary enables later native token streaming, cancellation, model-provider changes, ReMe lifecycle integration, and deterministic adapter testing.

## Tech Stack

- Java 17
- Spring Boot 3.3.5
- AgentScope Java 2.0.2
- `agentscope-extensions-reme` 1.0.12 is a legacy, currently disabled adapter and is not the target ReMe integration
- DashScope through its OpenAI-compatible endpoint
- Reactor types may remain inside infrastructure; application contracts must use LMS-owned types

## Commands

```powershell
# Compile lms-ai and required reactor modules
mvn -s mvn-settings.xml -pl lms-ai -am -DskipTests package

# Run lms-ai and required-module tests
mvn -s mvn-settings.xml -pl lms-ai -am test

# Inspect changed files
git diff -- lms-ai CAPABILITY_MAP-ai-harness.md SPEC-runtime-integration.md tasks
```

## Target Project Structure

```text
lms-ai/src/main/java/com/lms/ai/
├── application/
│   ├── conversation/
│   │   └── (AgentChatService migration, performed after the port and adapter exist)
│   └── port/
│       └── out/
│           ├── AgentRuntime.java
│           ├── AgentInvocation.java
│           └── AgentInvocationResult.java
├── infrastructure/
│   └── ai/
│       ├── agentscope/
│       │   └── AgentScopeRuntimeAdapter.java
│       └── dashscope/
│           └── (model-provider implementation, moved in a later bounded task)
```

The first implementation does not move every model-related class. It establishes the boundary and migrates one complete conversation path before further mechanical package moves.

## Contracts and Style

Application-owned records contain only LMS concepts:

```java
public interface AgentRuntime {
    AgentInvocationResult invoke(AgentInvocation invocation);
}

public record AgentInvocation(
        Long userId,
        Integer userType,
        Long sessionId,
        String agentType,
        String intentHint,
        List<ConversationMessage> messages) {
}
```

- No `io.agentscope.*` type may appear in the application-facing interface.
- Input and result types are immutable records where practical.
- Runtime exceptions are translated into LMS-owned exceptions at the adapter boundary.
- Existing error messages exposed by the current HTTP API remain compatible during this slice.

## Testing Strategy

- Unit-test conversation orchestration with a fake `AgentRuntime`; no real model call is required.
- Unit-test the AgentScope adapter mapping separately from application behavior.
- Keep the existing `AgentControllerAuthorizationTest` passing.
- Compile the complete `lms-ai` dependency path after every package move.
- Do not describe native token streaming as complete; this slice preserves the existing blocking call.

## Boundaries

### Always

- Preserve existing `/agent/**` contracts and authorization behavior.
- Preserve Session messages, profile injection, intent hints, Harness checks, Trace, and task recording.
- Move code in small compilable steps and retain focused tests.
- Keep secrets in environment/configuration sources; never print API keys.

### Ask first

- Adding or upgrading AgentScope/ReMe dependencies.
- Changing configuration keys or their defaults.
- Changing database schemas or Redis key formats.
- Changing public error bodies or SSE event contracts.

### Never

- Restore the legacy ReMe endpoint as the target implementation.
- Claim native token streaming before model deltas are propagated end to end.
- Couple application records to AgentScope, DashScope, Feign, MyBatis, or Redis types.
- Revert or reorganize unrelated user changes in the dirty worktree.

## Success Criteria

- `AgentChatService` invokes an LMS-owned `AgentRuntime` contract rather than `ReActAgent` directly.
- AgentScope construction, `RuntimeContext`, message conversion, blocking invocation, and Agent closing are owned by `AgentScopeRuntimeAdapter`.
- Existing chat, role binding, Session persistence, intent Trace, Harness usage accounting, and task-tree behavior remain unchanged.
- Focused authorization tests and the `lms-ai` Maven build pass.
- No new external dependency is required for this restructuring slice.

## Known Follow-up Work

- Native token streaming and cancellation receive a separate specification after this boundary is stable.
- Latest ReMe HTTP/MCP integration replaces the disabled legacy adapter under `infrastructure/memory/reme`.
- Model-provider abstractions and sub-Agent construction are migrated in later bounded tasks.

## Open Questions

None for the scope of this module specification. Implementation still requires plan and task approval.
