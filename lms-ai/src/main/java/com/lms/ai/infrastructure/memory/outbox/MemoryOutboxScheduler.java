package com.lms.ai.infrastructure.memory.outbox;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Polls durable ReMe delivery rows; disabled together with ReMe. */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "lms.ai.agent.reme", name = "enabled", havingValue = "true")
public class MemoryOutboxScheduler {

    private final MemoryOutboxService outboxService;

    @Scheduled(fixedDelayString = "${lms.ai.agent.reme.outbox-poll-ms:10000}", initialDelay = 10_000)
    public void deliver() {
        try {
            int delivered = outboxService.deliverDue();
            if (delivered > 0) {
                log.info("event=memory_outbox_batch entryPoint=memory_outbox_scheduler delivered={}", delivered);
            }
        } catch (Exception e) {
            log.error("event=memory_outbox_poll_failed entryPoint=memory_outbox_scheduler errorType={}",
                    e.getClass().getSimpleName());
        }
    }
}
