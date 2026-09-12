package com.lms.ai.chat;

import com.lms.ai.application.context.ContextAssembler;
import com.lms.ai.application.context.ContextAssemblyRequest;
import com.lms.ai.application.context.ConversationContext;
import com.lms.ai.application.memory.SessionClosureService;
import com.lms.ai.application.port.out.AgentInvocation;
import com.lms.ai.application.port.out.AgentInvocationResult;
import com.lms.ai.application.port.out.AgentRuntime;
import com.lms.ai.harness.HarnessSessionGuard;
import com.lms.ai.harness.HarnessTraceService;
import com.lms.ai.intent.Intent;
import com.lms.ai.intent.IntentDecision;
import com.lms.ai.intent.IntentRouterService;
import com.lms.ai.memory.ProfileService;
import com.lms.ai.session.AgentSession;
import com.lms.ai.session.AgentSessionService;
import com.lms.ai.task.TaskService;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentChatServiceTest {

    @Test
    void assemblesApplicationInvocationWithoutLeakingAgentScopeTypes() {
        AgentSessionService sessions = mock(AgentSessionService.class);
        ProfileService profiles = mock(ProfileService.class);
        HarnessSessionGuard guard = mock(HarnessSessionGuard.class);
        IntentRouterService router = mock(IntentRouterService.class);
        HarnessTraceService traces = mock(HarnessTraceService.class);
        TaskService tasks = mock(TaskService.class);
        ContextAssembler contextAssembler = mock(ContextAssembler.class);
        AtomicReference<AgentInvocation> captured = new AtomicReference<>();
        AgentRuntime runtime = invocation -> {
            captured.set(invocation);
            return new AgentInvocationResult("answer");
        };

        AgentSession session = new AgentSession();
        session.setId(99L);
        session.setUserId(7L);
        session.setAgentType("student-agent");
        session.setStatus(AgentSession.STATUS_ACTIVE);
        when(sessions.getOwned(7L, 99L)).thenReturn(session);
        when(contextAssembler.assemble(new ContextAssemblyRequest(
                7L, 1, 99L, "student-agent", "知识库里怎么说？")))
                .thenReturn(new ConversationContext(List.of(
                        AgentInvocation.Message.user("earlier question"),
                        AgentInvocation.Message.assistant("earlier answer"),
                        AgentInvocation.Message.user("知识库里怎么说？")), "偏好分步骤讲解"));
        when(router.route(7L, 1, 99L, "知识库里怎么说？", "student-agent"))
                .thenReturn(IntentDecision.rule(Intent.KB_RAG, "use kb"));

        AgentChatService service = new AgentChatService(
                runtime, sessions, profiles, guard, router, traces, tasks, contextAssembler,
                mock(SessionClosureService.class));

        AgentChatService.ChatResult result = service.chat(
                7L, 1, 99L, "知识库里怎么说？", "student-agent");

        assertThat(result.sessionId()).isEqualTo(99L);
        assertThat(result.reply()).isEqualTo("answer");
        AgentInvocation invocation = captured.get();
        assertThat(invocation.userId()).isEqualTo(7L);
        assertThat(invocation.userType()).isEqualTo(1);
        assertThat(invocation.sessionId()).isEqualTo(99L);
        assertThat(invocation.agentType()).isEqualTo("student-agent");
        assertThat(invocation.intent()).isEqualTo("KB_RAG");
        assertThat(invocation.intentHint()).isEqualTo("use kb");
        assertThat(invocation.longTermMemoryContext()).isEqualTo("偏好分步骤讲解");
        assertThat(invocation.messages()).extracting(AgentInvocation.Message::text)
                .containsExactly("earlier question", "earlier answer", "知识库里怎么说？");
        assertThat(invocation.messages()).extracting(AgentInvocation.Message::role)
                .containsExactly(
                        AgentInvocation.Role.USER,
                        AgentInvocation.Role.ASSISTANT,
                        AgentInvocation.Role.USER);
        verify(sessions).appendMessage(99L, "user", "知识库里怎么说？");
        verify(sessions).appendMessage(99L, "assistant", "answer");
        verify(guard).recordAssistant(99L, "answer");
    }
}
