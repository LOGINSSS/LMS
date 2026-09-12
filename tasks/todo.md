# Task List: runtime-integration

## Task 1: Define LMS-owned runtime contracts

**Acceptance criteria:**
- [x] Invocation messages, request, result, and runtime port are under `application/port/out`.
- [x] No contract imports AgentScope, Feign, MyBatis, Redis, or Reactor types.

**Verification:**
- [x] Focused contract test compiles and passes.

**Dependencies:** None

**Estimated scope:** Small

## Task 2: Add AgentScope runtime adapter

**Acceptance criteria:**
- [x] Adapter converts LMS messages to AgentScope messages and builds the current RuntimeContext.
- [x] Adapter delegates through `PersonalAgentFactory` and always closes the Agent.
- [x] Adapter returns an LMS-owned result.

**Verification:**
- [x] `AgentScopeRuntimeAdapterTest` passes.
- [x] `lms-ai` compiles.

**Dependencies:** Task 1

**Estimated scope:** Medium

## Checkpoint: Runtime boundary

- [x] Application contract contains no vendor runtime types.
- [x] Focused runtime tests pass.
- [x] Module compiles.

## Task 3: Migrate the conversation service

**Acceptance criteria:**
- [x] `AgentChatService` depends on `AgentRuntime`, not `PersonalAgentFactory` or AgentScope types.
- [x] Session, context compression, intent, Harness, Trace, and task behavior remain present.

**Verification:**
- [x] `AgentChatServiceTest` passes.

**Dependencies:** Task 2

**Estimated scope:** Medium

## Task 4: Run compatibility verification

**Acceptance criteria:**
- [x] Existing authorization tests pass.
- [x] All `lms-ai` tests pass.
- [x] Maven package succeeds.

**Dependencies:** Task 3

**Estimated scope:** Small

## Checkpoint: Behavior compatibility

- [x] Focused tests pass.
- [x] Full `lms-ai` test suite passes.
- [x] Package build succeeds.

## Task 5: Review the implementation

**Acceptance criteria:**
- [x] Scoped diff contains no unrelated edits.
- [x] No security, lifecycle, or dependency-direction regression remains.

**Verification:**
- [x] Manual code review completed with findings recorded in `AI_MODULE_ASSESSMENT.md`.

**Dependencies:** Task 4

**Estimated scope:** Small

## Task 6: Assess the complete AI module

**Acceptance criteria:**
- [x] Current features are mapped to source packages and verified evidence.
- [x] Disabled, partial, and complete Harness capabilities are clearly distinguished.
- [x] Remaining architecture and implementation gaps are prioritized.

**Verification:**
- [x] Final report cites local files and executed test/build results.

**Dependencies:** Task 5

**Estimated scope:** Medium

## Task 7: Close P0 Harness tool-policy gaps

**Acceptance criteria:**
- [x] Unknown tool metadata is denied.
- [x] Missing domains, missing declared methods, and wrapper failures abort Toolkit construction.
- [x] An Agent receives only the exact tool methods declared in its YAML.
- [x] Duplicate function names across domains in one Agent are rejected before Toolkit registration.
- [x] The `grab` domain is registered.

**Verification:**
- [x] Fail-closed and exact-allowlist tests pass.

## Task 8: Enforce Task identity and ownership

**Acceptance criteria:**
- [x] HTTP task publication is teacher-only and uses the current teacher identity.
- [x] HTTP and Agent-tool query/cancel operations are owner-scoped.
- [x] Missing HTTP or RuntimeContext identity is rejected before task access.
- [x] Pending tasks with nullable result fields serialize safely.

**Verification:**
- [x] Controller, service, and Agent-tool authorization tests pass.
- [x] Full `lms-ai` suite passes with 24 tests.

## Task 9: Separate Context assembly and long-term-memory boundary

**Acceptance criteria:**
- [x] Context assembly lives under `application/context` and owns history, compression, current-message, and memory assembly.
- [x] Application depends on `LongTermMemoryPort`, not AgentScope or ReMe memory types.
- [x] Existing MySQL profile memory is exposed through an infrastructure adapter.
- [x] Agent invocation carries the assembled memory context into the runtime adapter and personal Agent prompt.
- [x] Recall failures do not block chat, and Application enforces at most five injected fragments.

**Verification:**
- [x] Context, profile-memory, chat, and AgentScope adapter focused tests pass.
- [x] Full `lms-ai` suite passes with 29 tests.
- [x] Package build succeeds.

## Task 10: Integrate the current ReMe personal Wiki API

**Acceptance criteria:**
- [x] ReMe is called through LMS-owned ports and the current HTTP Job API.
- [x] Recall is capped at Top-5 and falls back to MySQL profile memory without blocking chat.
- [x] Users can explicitly write, correct, and delete server-addressed Markdown memories.
- [x] User input cannot select a physical path or ReMe endpoint.
- [x] Enabled configuration requires a per-user endpoint template; shared-workspace search is rejected.
- [x] Deprecated `agentscope-extensions-reme` runtime integration is removed.

**Verification:**
- [x] ReMe property, HTTP contract, and adapter tests pass (9 tests).
- [x] At this checkpoint, the full `lms-ai` test suite passed with 38 tests.
- [x] Package build succeeds and contains the new HTTP adapter without the old extension.

## Task 11: Persist completed sessions through ReMe Auto Memory

**Acceptance criteria:**
- [x] Session closure captures the source transcript before Redis cleanup.
- [x] ReMe receives a stable session id and `role/content/created_at` source messages.
- [x] Only bounded user/assistant content is eligible; tool messages and unbounded payloads are excluded.
- [x] Session state and Redis cleanup complete before asynchronous memory persistence.
- [x] Redis read, executor rejection, and ReMe failures never prevent session closure.
- [x] Current ReMe `/write` contract includes required `name` and `description` fields.

**Verification:**
- [x] Session closure and ReMe focused tests pass (15 tests).
- [x] Full `lms-ai` test suite passes with 45 tests.
- [x] Package build succeeds.

## Task 12: Reliable ReMe delivery Outbox

**Status:** Complete in code; deployment migration and live ReMe verification remain operational steps.

**Acceptance criteria:**
- [x] Session close and memory enqueue share one MySQL transaction.
- [x] Redis source messages are removed only after that transaction commits.
- [x] A bounded scheduler claims due rows with a recoverable processing lease.
- [x] ReMe failures use exponential retry and move to a dead-letter state after the configured limit.
- [x] Harness Trace records queued, delivered, retry and dead events without message bodies or provider error text.
- [x] Successful rows discard their conversation payload and retain only delivery audit metadata.
- [x] Fresh and existing database scripts define `agent_memory_outbox`.

**Deployment note:**
- Apply `docker/mysql/init/92-v04-memory-outbox.sql` to an existing `lms_ai` database before enabling ReMe.
