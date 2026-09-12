package com.lms.ai.infrastructure.memory.outbox;

import com.lms.ai.application.port.out.SessionMemoryPort;
import com.lms.ai.config.ReMeProperties;
import com.lms.ai.harness.HarnessTraceService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MemoryOutboxServiceTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-09-12T00:00:00Z"), ZoneOffset.UTC);

    @Test
    void disabledReMeDoesNotAccumulateOutboxRows() {
        SessionMemoryOutboxMapper mapper = mock(SessionMemoryOutboxMapper.class);
        ReMeProperties properties = properties(false);
        MemoryOutboxService service = service(mapper, request -> { }, properties);

        service.enqueue(request());

        verify(mapper, never()).insert(any(SessionMemoryOutbox.class));
    }

    @Test
    void enqueueStoresCapturePayloadForOneSession() {
        SessionMemoryOutboxMapper mapper = mock(SessionMemoryOutboxMapper.class);
        MemoryOutboxService service = service(mapper, request -> { }, properties(true));

        service.enqueue(request());

        ArgumentCaptor<SessionMemoryOutbox> row = ArgumentCaptor.forClass(SessionMemoryOutbox.class);
        verify(mapper).insert(row.capture());
        assertThat(row.getValue().getSessionId()).isEqualTo(99L);
        assertThat(row.getValue().getStatus()).isEqualTo(SessionMemoryOutbox.STATUS_PENDING);
        assertThat(row.getValue().getAttempts()).isZero();
        assertThat(row.getValue().getPayload()).contains("lms-agent-99", "偏好案例学习");
    }

    @Test
    void successfulDeliveryMarksClaimedRowDone() {
        SessionMemoryOutboxMapper mapper = mock(SessionMemoryOutboxMapper.class);
        SessionMemoryPort memory = mock(SessionMemoryPort.class);
        MemoryOutboxService service = service(mapper, memory, properties(true));
        when(mapper.selectList(any())).thenReturn(List.of(pending(1L, 0)));
        when(mapper.update(any(), any())).thenReturn(1);

        assertThat(service.deliverDue()).isEqualTo(1);

        verify(memory).capture(request());
        ArgumentCaptor<SessionMemoryOutbox> update = ArgumentCaptor.forClass(SessionMemoryOutbox.class);
        verify(mapper, org.mockito.Mockito.atLeast(2)).update(update.capture(), any());
        assertThat(update.getAllValues()).anySatisfy(value -> {
            assertThat(value.getStatus()).isEqualTo(SessionMemoryOutbox.STATUS_DONE);
            assertThat(value.getFinishTime()).isEqualTo(LocalDateTime.ofInstant(CLOCK.instant(), ZoneOffset.UTC));
            assertThat(value.getPayload()).isEqualTo("{}");
        });
    }

    @Test
    void failedDeliverySchedulesExponentialRetryWithoutPersistingErrorMessage() {
        SessionMemoryOutboxMapper mapper = mock(SessionMemoryOutboxMapper.class);
        SessionMemoryPort memory = request -> { throw new IllegalStateException("secret provider response"); };
        ReMeProperties properties = properties(true);
        properties.setRetryBaseSeconds(30);
        MemoryOutboxService service = service(mapper, memory, properties);
        when(mapper.selectList(any())).thenReturn(List.of(pending(1L, 1)));
        when(mapper.update(any(), any())).thenReturn(1);

        service.deliverDue();

        ArgumentCaptor<SessionMemoryOutbox> update = ArgumentCaptor.forClass(SessionMemoryOutbox.class);
        verify(mapper, org.mockito.Mockito.atLeast(2)).update(update.capture(), any());
        assertThat(update.getAllValues()).anySatisfy(value -> {
            assertThat(value.getStatus()).isEqualTo(SessionMemoryOutbox.STATUS_RETRY);
            assertThat(value.getAttempts()).isEqualTo(2);
            assertThat(value.getNextAttemptTime()).isEqualTo(LocalDateTime.of(2026, 9, 12, 0, 1));
            assertThat(value.getLastErrorType()).isEqualTo("IllegalStateException");
        });
        assertThat(update.getAllValues()).filteredOn(value -> value.getLastErrorType() != null).allSatisfy(value ->
                assertThat(value.getLastErrorType()).doesNotContain("secret provider response"));
    }

    @Test
    void exhaustedDeliveryMovesRowToDeadLetterState() {
        SessionMemoryOutboxMapper mapper = mock(SessionMemoryOutboxMapper.class);
        SessionMemoryPort memory = request -> { throw new IllegalStateException("down"); };
        ReMeProperties properties = properties(true);
        properties.setMaxAttempts(3);
        MemoryOutboxService service = service(mapper, memory, properties);
        when(mapper.selectList(any())).thenReturn(List.of(pending(1L, 2)));
        when(mapper.update(any(), any())).thenReturn(1);

        service.deliverDue();

        ArgumentCaptor<SessionMemoryOutbox> update = ArgumentCaptor.forClass(SessionMemoryOutbox.class);
        verify(mapper, org.mockito.Mockito.atLeast(2)).update(update.capture(), any());
        assertThat(update.getAllValues()).anySatisfy(value -> {
            assertThat(value.getStatus()).isEqualTo(SessionMemoryOutbox.STATUS_DEAD);
            assertThat(value.getAttempts()).isEqualTo(3);
            assertThat(value.getFinishTime()).isEqualTo(LocalDateTime.ofInstant(CLOCK.instant(), ZoneOffset.UTC));
        });
    }

    private MemoryOutboxService service(SessionMemoryOutboxMapper mapper,
                                        SessionMemoryPort memory,
                                        ReMeProperties properties) {
        return new MemoryOutboxService(
                mapper, memory, properties, mock(HarnessTraceService.class), new ObjectMapper(), CLOCK);
    }

    private ReMeProperties properties(boolean enabled) {
        ReMeProperties properties = new ReMeProperties();
        properties.setEnabled(enabled);
        properties.setEndpointTemplate("http://reme/users/{userId}");
        return properties;
    }

    private SessionMemoryPort.CaptureRequest request() {
        return new SessionMemoryPort.CaptureRequest(7L, "lms-agent-99", List.of(
                new SessionMemoryPort.SourceMessage("user", "偏好案例学习", "2026-09-11T12:00:00Z")));
    }

    private SessionMemoryOutbox pending(Long id, int attempts) {
        SessionMemoryOutbox row = new SessionMemoryOutbox();
        row.setId(id);
        row.setEventId("evt-1");
        row.setUserId(7L);
        row.setSessionId(99L);
        try {
            row.setPayload(new ObjectMapper().writeValueAsString(request()));
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new AssertionError(e);
        }
        row.setStatus(SessionMemoryOutbox.STATUS_PENDING);
        row.setAttempts(attempts);
        row.setNextAttemptTime(LocalDateTime.ofInstant(CLOCK.instant(), ZoneOffset.UTC));
        return row;
    }
}
