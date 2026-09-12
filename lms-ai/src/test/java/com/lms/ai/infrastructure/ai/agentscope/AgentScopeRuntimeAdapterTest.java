package com.lms.ai.infrastructure.ai.agentscope;

import com.lms.ai.application.port.out.AgentInvocation;
import com.lms.ai.application.port.out.AgentInvocationResult;
import com.lms.ai.factory.PersonalAgentFactory;
import com.lms.ai.harness.HarnessKeys;
import com.lms.ai.tools.ToolSupport;
import io.agentscope.core.ReActAgent;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import reactor.core.publisher.Mono;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentScopeRuntimeAdapterTest {

    @Test
    void mapsApplicationInvocationToAgentScopeAndClosesAgent() {
        PersonalAgentFactory factory = mock(PersonalAgentFactory.class);
        ReActAgent agent = mock(ReActAgent.class);
        Msg response = mock(Msg.class);
        when(response.getTextContent()).thenReturn("runtime answer");
        when(factory.build(7L, 1, "student-agent", "route hint", "profile memory")).thenReturn(agent);
        when(agent.call(anyList(), any(RuntimeContext.class))).thenReturn(Mono.just(response));

        AgentScopeRuntimeAdapter adapter = new AgentScopeRuntimeAdapter(factory);
        AgentInvocation invocation = new AgentInvocation(
                7L,
                1,
                99L,
                "student-agent",
                "KB_RAG",
                "route hint",
                "profile memory",
                List.of(
                        AgentInvocation.Message.user("question"),
                        AgentInvocation.Message.assistant("previous answer")));

        AgentInvocationResult result = adapter.invoke(invocation);

        assertThat(result.reply()).isEqualTo("runtime answer");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Msg>> messagesCaptor = ArgumentCaptor.forClass(List.class);
        ArgumentCaptor<RuntimeContext> contextCaptor = ArgumentCaptor.forClass(RuntimeContext.class);
        verify(agent).call(messagesCaptor.capture(), contextCaptor.capture());
        verify(agent).close();

        List<Msg> messages = messagesCaptor.getValue();
        assertThat(messages).hasSize(2);
        assertThat(messages.get(0).getRole()).isEqualTo(MsgRole.USER);
        assertThat(messages.get(0).getTextContent()).isEqualTo("question");
        assertThat(messages.get(1).getRole()).isEqualTo(MsgRole.ASSISTANT);
        assertThat(messages.get(1).getTextContent()).isEqualTo("previous answer");

        RuntimeContext context = contextCaptor.getValue();
        assertThat(context.getSessionId()).isEqualTo("99");
        assertThat(context.getUserId()).isEqualTo("7");
        assertThat(context.get(ToolSupport.CTX_USER_ID, Long.class)).isEqualTo(7L);
        assertThat(context.get(ToolSupport.CTX_USER_TYPE, Integer.class)).isEqualTo(1);
        assertThat(context.get(HarnessKeys.CTX_INTENT, String.class)).isEqualTo("KB_RAG");
    }

    @Test
    void closesAgentWhenRuntimeCallFails() {
        PersonalAgentFactory factory = mock(PersonalAgentFactory.class);
        ReActAgent agent = mock(ReActAgent.class);
        when(factory.build(7L, 1, "student-agent", null, null)).thenReturn(agent);
        when(agent.call(anyList(), any(RuntimeContext.class)))
                .thenReturn(Mono.error(new IllegalStateException("model unavailable")));

        AgentScopeRuntimeAdapter adapter = new AgentScopeRuntimeAdapter(factory);
        AgentInvocation invocation = new AgentInvocation(
                7L, 1, 99L, "student-agent", null, null, null,
                List.of(AgentInvocation.Message.user("question")));

        assertThatThrownBy(() -> adapter.invoke(invocation))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("model unavailable");
        verify(agent).close();
    }
}
