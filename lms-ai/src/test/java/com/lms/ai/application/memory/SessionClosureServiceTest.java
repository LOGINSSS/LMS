package com.lms.ai.application.memory;

import com.lms.ai.application.port.out.SessionMemoryOutboxPort;
import com.lms.ai.application.port.out.SessionMemoryPort;
import com.lms.ai.session.AgentSession;
import com.lms.ai.session.AgentSessionService;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SessionClosureServiceTest {

    @Test
    void persistsMemoryOutboxBeforeClosingSession() {
        AgentSessionService sessions = mock(AgentSessionService.class);
        when(sessions.getOwned(7L, 99L)).thenReturn(session(99L, AgentSession.STATUS_ACTIVE));
        when(sessions.loadMessagesRaw(99L)).thenReturn(List.of(
                Map.of("role", "user", "text", "偏好案例学习", "created_at", "2026-09-11T12:00:00Z"),
                Map.of("role", "assistant", "text", "已了解", "created_at", "2026-09-11T12:00:01Z")));
        SessionMemoryOutboxPort outbox = mock(SessionMemoryOutboxPort.class);
        SessionClosureService service = new SessionClosureService(sessions, outbox);

        service.close(7L, 99L);

        InOrder order = inOrder(sessions, outbox);
        order.verify(sessions).loadMessagesRaw(99L);
        order.verify(outbox).enqueue(new SessionMemoryPort.CaptureRequest(
                7L,
                "lms-agent-99",
                List.of(
                        new SessionMemoryPort.SourceMessage("user", "偏好案例学习", "2026-09-11T12:00:00Z"),
                        new SessionMemoryPort.SourceMessage("assistant", "已了解", "2026-09-11T12:00:01Z"))));
        order.verify(sessions).closeSession(7L, 99L);
    }

    @Test
    void outboxFailurePreventsDestructiveSessionCleanup() {
        AgentSessionService sessions = mock(AgentSessionService.class);
        when(sessions.getOwned(7L, 99L)).thenReturn(session(99L, AgentSession.STATUS_ACTIVE));
        when(sessions.loadMessagesRaw(99L)).thenReturn(List.of(Map.of("role", "user", "text", "hello")));
        SessionMemoryOutboxPort outbox = request -> { throw new IllegalStateException("db unavailable"); };
        SessionClosureService service = new SessionClosureService(sessions, outbox);

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.close(7L, 99L))
                .isInstanceOf(IllegalStateException.class);
        verify(sessions, never()).closeSession(7L, 99L);
    }

    @Test
    void sourceLoadFailureStillClosesSessionWithoutOutbox() {
        AgentSessionService sessions = mock(AgentSessionService.class);
        when(sessions.getOwned(7L, 99L)).thenReturn(session(99L, AgentSession.STATUS_ACTIVE));
        when(sessions.loadMessagesRaw(99L)).thenThrow(new IllegalStateException("redis down"));
        SessionMemoryOutboxPort outbox = mock(SessionMemoryOutboxPort.class);

        assertThatCode(() -> new SessionClosureService(sessions, outbox).close(7L, 99L))
                .doesNotThrowAnyException();
        verify(sessions).closeSession(7L, 99L);
        verify(outbox, never()).enqueue(any());
    }

    @Test
    void alreadyClosedSessionIsNotQueuedAgain() {
        AgentSessionService sessions = mock(AgentSessionService.class);
        when(sessions.getOwned(7L, 99L)).thenReturn(session(99L, AgentSession.STATUS_CLOSED));
        SessionMemoryOutboxPort outbox = mock(SessionMemoryOutboxPort.class);

        new SessionClosureService(sessions, outbox).close(7L, 99L);

        verify(sessions, never()).loadMessagesRaw(99L);
        verify(outbox, never()).enqueue(any());
    }

    private AgentSession session(Long id, int status) {
        AgentSession session = new AgentSession();
        session.setId(id);
        session.setUserId(7L);
        session.setStatus(status);
        return session;
    }
}
