package com.lms.ai.application.memory;

import com.lms.ai.application.port.out.SessionMemoryOutboxPort;
import com.lms.ai.application.port.out.SessionMemoryPort;
import com.lms.ai.session.AgentSession;
import com.lms.ai.session.AgentSessionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Atomically closes an owned session and schedules its source conversation for durable delivery. */
@Slf4j
@Service
public class SessionClosureService {

    private static final int MAX_SOURCE_MESSAGES = 40;
    private static final int MAX_MESSAGE_CHARS = 8_000;

    private final AgentSessionService sessionService;
    private final SessionMemoryOutboxPort memoryOutbox;

    public SessionClosureService(AgentSessionService sessionService,
                                 SessionMemoryOutboxPort memoryOutbox) {
        this.sessionService = sessionService;
        this.memoryOutbox = memoryOutbox;
    }

    @Transactional
    public void close(Long userId, Long sessionId) {
        AgentSession session = sessionService.getOwned(userId, sessionId);
        if (session.getStatus() == AgentSession.STATUS_CLOSED) {
            return;
        }

        List<Map<String, String>> source = loadSourceBestEffort(sessionId);
        List<SessionMemoryPort.SourceMessage> messages = normalize(source);
        if (!messages.isEmpty()) {
            memoryOutbox.enqueue(new SessionMemoryPort.CaptureRequest(
                    userId, "lms-agent-" + sessionId, messages));
        }
        sessionService.closeSession(userId, sessionId);
    }

    private List<Map<String, String>> loadSourceBestEffort(Long sessionId) {
        try {
            return sessionService.loadMessagesRaw(sessionId);
        } catch (Exception e) {
            log.warn("会话消息读取失败，继续关闭 sessionId={} errorType={}",
                    sessionId, e.getClass().getSimpleName());
            return List.of();
        }
    }

    private List<SessionMemoryPort.SourceMessage> normalize(List<Map<String, String>> source) {
        if (source == null || source.isEmpty()) {
            return List.of();
        }
        int from = Math.max(0, source.size() - MAX_SOURCE_MESSAGES);
        List<SessionMemoryPort.SourceMessage> messages = new ArrayList<>();
        for (Map<String, String> item : source.subList(from, source.size())) {
            String role = item.get("role");
            String content = item.get("text");
            if (!("user".equals(role) || "assistant".equals(role)) || content == null || content.isBlank()) {
                continue;
            }
            if (content.length() > MAX_MESSAGE_CHARS) {
                content = content.substring(0, MAX_MESSAGE_CHARS);
            }
            messages.add(new SessionMemoryPort.SourceMessage(role, content, item.get("created_at")));
        }
        return List.copyOf(messages);
    }
}
