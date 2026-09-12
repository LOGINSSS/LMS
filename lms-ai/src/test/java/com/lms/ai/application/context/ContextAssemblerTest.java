package com.lms.ai.application.context;

import com.lms.ai.application.port.out.AgentInvocation;
import com.lms.ai.application.port.out.LongTermMemoryPort;
import com.lms.ai.session.AgentSessionService;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ContextAssemblerTest {

    @Test
    void assemblesShortTermHistoryCurrentMessageAndLongTermMemory() {
        AgentSessionService sessions = mock(AgentSessionService.class);
        ContextCompressor compressor = mock(ContextCompressor.class);
        when(sessions.loadMessages(99L)).thenReturn(List.of(
                Map.of("role", "user", "text", "earlier question"),
                Map.of("role", "assistant", "text", "earlier answer")));
        AtomicReference<LongTermMemoryPort.RecallRequest> captured = new AtomicReference<>();
        LongTermMemoryPort memory = request -> {
            captured.set(request);
            return List.of(new LongTermMemoryPort.MemoryFragment("profile", "偏好简洁解释"));
        };
        ContextAssembler assembler = new ContextAssembler(sessions, compressor, memory);

        ConversationContext context = assembler.assemble(new ContextAssemblyRequest(
                7L, 1, 99L, "student-agent", "当前问题"));

        verify(compressor).compressIfNeeded(99L);
        assertThat(context.messages()).extracting(AgentInvocation.Message::text)
                .containsExactly("earlier question", "earlier answer", "当前问题");
        assertThat(context.messages()).extracting(AgentInvocation.Message::role)
                .containsExactly(AgentInvocation.Role.USER, AgentInvocation.Role.ASSISTANT,
                        AgentInvocation.Role.USER);
        assertThat(context.longTermMemoryContext()).isEqualTo("偏好简洁解释");
        assertThat(captured.get()).isEqualTo(new LongTermMemoryPort.RecallRequest(
                7L, 1, "student-agent", "当前问题", 5));
    }

    @Test
    void memoryAndCompressionFailuresDoNotBlockConversationContext() {
        AgentSessionService sessions = mock(AgentSessionService.class);
        ContextCompressor compressor = mock(ContextCompressor.class);
        when(compressor.compressIfNeeded(99L)).thenThrow(new IllegalStateException("summary unavailable"));
        when(sessions.loadMessages(99L)).thenReturn(List.of());
        LongTermMemoryPort memory = request -> {
            throw new IllegalStateException("memory unavailable");
        };
        ContextAssembler assembler = new ContextAssembler(sessions, compressor, memory);

        ConversationContext context = assembler.assemble(new ContextAssemblyRequest(
                7L, 1, 99L, "student-agent", "当前问题"));

        assertThat(context.messages()).containsExactly(AgentInvocation.Message.user("当前问题"));
        assertThat(context.longTermMemoryContext()).isNull();
    }

    @Test
    void capsInjectedMemoryAtFiveFragmentsEvenWhenAdapterReturnsMore() {
        AgentSessionService sessions = mock(AgentSessionService.class);
        when(sessions.loadMessages(99L)).thenReturn(List.of());
        LongTermMemoryPort memory = request -> List.of(
                fragment("one"), fragment("two"), fragment("three"),
                fragment("four"), fragment("five"), fragment("six"));
        ContextAssembler assembler = new ContextAssembler(
                sessions, mock(ContextCompressor.class), memory);

        ConversationContext context = assembler.assemble(new ContextAssemblyRequest(
                7L, 1, 99L, "student-agent", "当前问题"));

        assertThat(context.longTermMemoryContext()).isEqualTo("one\ntwo\nthree\nfour\nfive");
    }

    private LongTermMemoryPort.MemoryFragment fragment(String content) {
        return new LongTermMemoryPort.MemoryFragment("wiki", content);
    }
}
