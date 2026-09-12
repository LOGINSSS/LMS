package com.lms.ai.infrastructure.memory.outbox;

import cn.hutool.core.util.IdUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lms.ai.application.port.out.SessionMemoryOutboxPort;
import com.lms.ai.application.port.out.SessionMemoryPort;
import com.lms.ai.config.ReMeProperties;
import com.lms.ai.harness.HarnessTraceService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/** MySQL Outbox with bounded retries and a recoverable processing lease. */
@Slf4j
@Service
public class MemoryOutboxService implements SessionMemoryOutboxPort {

    private static final String ENTRY_POINT = "memory_outbox_scheduler";

    private final SessionMemoryOutboxMapper mapper;
    private final SessionMemoryPort memory;
    private final ReMeProperties properties;
    private final HarnessTraceService traceService;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    @Autowired
    public MemoryOutboxService(SessionMemoryOutboxMapper mapper,
                               SessionMemoryPort memory,
                               ReMeProperties properties,
                               HarnessTraceService traceService,
                               ObjectMapper objectMapper) {
        this(mapper, memory, properties, traceService, objectMapper, Clock.systemDefaultZone());
    }

    MemoryOutboxService(SessionMemoryOutboxMapper mapper,
                        SessionMemoryPort memory,
                        ReMeProperties properties,
                        HarnessTraceService traceService,
                        ObjectMapper objectMapper,
                        Clock clock) {
        this.mapper = mapper;
        this.memory = memory;
        this.properties = properties;
        this.traceService = traceService;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Override
    public void enqueue(SessionMemoryPort.CaptureRequest request) {
        if (!properties.isEnabled()) {
            return;
        }
        SessionMemoryOutbox row = new SessionMemoryOutbox();
        row.setEventId(IdUtil.fastSimpleUUID());
        row.setUserId(request.userId());
        row.setSessionId(parseSessionId(request.sessionId()));
        row.setPayload(writePayload(request));
        row.setStatus(SessionMemoryOutbox.STATUS_PENDING);
        row.setAttempts(0);
        row.setNextAttemptTime(now());
        mapper.insert(row);
        trace(row, HarnessTraceService.EVENT_MEMORY_QUEUED, Map.of("attempt", 0));
    }

    /** Claims and delivers one bounded batch. Returns the number delivered successfully. */
    public int deliverDue() {
        if (!properties.isEnabled()) {
            return 0;
        }
        LocalDateTime now = now();
        List<SessionMemoryOutbox> due = mapper.selectList(new LambdaQueryWrapper<SessionMemoryOutbox>()
                .and(q -> q.in(SessionMemoryOutbox::getStatus,
                                SessionMemoryOutbox.STATUS_PENDING, SessionMemoryOutbox.STATUS_RETRY)
                        .le(SessionMemoryOutbox::getNextAttemptTime, now)
                        .or(stale -> stale.eq(SessionMemoryOutbox::getStatus, SessionMemoryOutbox.STATUS_PROCESSING)
                                .le(SessionMemoryOutbox::getNextAttemptTime, now)))
                .orderByAsc(SessionMemoryOutbox::getNextAttemptTime)
                .last("LIMIT " + properties.getOutboxBatchSize()));
        int delivered = 0;
        for (SessionMemoryOutbox candidate : due) {
            int attempt = candidate.getAttempts() + 1;
            if (!claim(candidate, attempt, now)) {
                continue;
            }
            try {
                SessionMemoryPort.CaptureRequest request = objectMapper.readValue(
                        candidate.getPayload(), SessionMemoryPort.CaptureRequest.class);
                memory.capture(request);
                markDone(candidate, attempt, now);
                delivered++;
            } catch (Exception e) {
                markFailed(candidate, attempt, now, e);
            }
        }
        return delivered;
    }

    private boolean claim(SessionMemoryOutbox candidate, int attempt, LocalDateTime now) {
        SessionMemoryOutbox patch = new SessionMemoryOutbox();
        patch.setStatus(SessionMemoryOutbox.STATUS_PROCESSING);
        patch.setAttempts(attempt);
        patch.setNextAttemptTime(now.plusSeconds(properties.getProcessingLeaseSeconds()));
        patch.setLastErrorType(null);
        return mapper.update(patch, new LambdaUpdateWrapper<SessionMemoryOutbox>()
                .eq(SessionMemoryOutbox::getId, candidate.getId())
                .eq(SessionMemoryOutbox::getStatus, candidate.getStatus())
                .le(SessionMemoryOutbox::getNextAttemptTime, now)) == 1;
    }

    private void markDone(SessionMemoryOutbox row, int attempt, LocalDateTime now) {
        SessionMemoryOutbox patch = new SessionMemoryOutbox();
        patch.setStatus(SessionMemoryOutbox.STATUS_DONE);
        patch.setAttempts(attempt);
        patch.setFinishTime(now);
        patch.setPayload("{}");
        patch.setNextAttemptTime(null);
        if (mapper.update(patch, processingRow(row.getId())) != 1) {
            throw new IllegalStateException("长期记忆 Outbox 完成状态回写冲突");
        }
        trace(row, HarnessTraceService.EVENT_MEMORY_DELIVERED, Map.of("attempt", attempt));
    }

    private void markFailed(SessionMemoryOutbox row, int attempt, LocalDateTime now, Exception error) {
        boolean exhausted = attempt >= properties.getMaxAttempts();
        SessionMemoryOutbox patch = new SessionMemoryOutbox();
        patch.setStatus(exhausted ? SessionMemoryOutbox.STATUS_DEAD : SessionMemoryOutbox.STATUS_RETRY);
        patch.setAttempts(attempt);
        patch.setLastErrorType(error.getClass().getSimpleName());
        if (exhausted) {
            patch.setFinishTime(now);
            patch.setNextAttemptTime(null);
        } else {
            patch.setNextAttemptTime(now.plusSeconds(retryDelaySeconds(attempt)));
        }
        mapper.update(patch, processingRow(row.getId()));
        String event = exhausted ? HarnessTraceService.EVENT_MEMORY_DEAD : HarnessTraceService.EVENT_MEMORY_RETRY;
        trace(row, event, Map.of("attempt", attempt, "errorType", error.getClass().getSimpleName()));
        log.warn("event={} entryPoint={} eventId={} sessionId={} attempt={} errorType={}",
                event, ENTRY_POINT, row.getEventId(), row.getSessionId(), attempt, error.getClass().getSimpleName());
    }

    private LambdaUpdateWrapper<SessionMemoryOutbox> processingRow(Long id) {
        return new LambdaUpdateWrapper<SessionMemoryOutbox>()
                .eq(SessionMemoryOutbox::getId, id)
                .eq(SessionMemoryOutbox::getStatus, SessionMemoryOutbox.STATUS_PROCESSING);
    }

    private long retryDelaySeconds(int attempt) {
        int exponent = Math.min(attempt - 1, 10);
        return Math.multiplyExact(properties.getRetryBaseSeconds(), 1L << exponent);
    }

    private Long parseSessionId(String sessionId) {
        if (sessionId == null || !sessionId.startsWith("lms-agent-")) {
            throw new IllegalArgumentException("长期记忆 sessionId 格式非法");
        }
        return Long.valueOf(sessionId.substring("lms-agent-".length()));
    }

    private String writePayload(SessionMemoryPort.CaptureRequest request) {
        try {
            return objectMapper.writeValueAsString(request);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("长期记忆请求无法序列化", e);
        }
    }

    private LocalDateTime now() {
        return LocalDateTime.ofInstant(clock.instant(), clock.getZone());
    }

    private void trace(SessionMemoryOutbox row, String event, Map<String, Object> detail) {
        traceService.record(String.valueOf(row.getSessionId()), event, Map.of(
                "entryPoint", ENTRY_POINT,
                "eventId", row.getEventId(),
                "detail", detail));
    }
}
