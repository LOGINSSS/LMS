package com.lms.ai.chat;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** SSE transport for Copilot lifecycle events and progressively rendered answers. */
@Slf4j
@Service
public class AgentStreamService {

    private static final long TIMEOUT_MILLIS = 180_000L;
    private static final int CHUNK_CODE_POINTS = 24;
    private static final Pattern HITL_ID = Pattern.compile("hitl-[A-Za-z0-9]+", Pattern.CASE_INSENSITIVE);

    private final AgentChatService chatService;
    @Qualifier("agentStreamExecutor")
    private final Executor executor;

    public AgentStreamService(AgentChatService chatService,
                              @Qualifier("agentStreamExecutor") Executor executor) {
        this.chatService = chatService;
        this.executor = executor;
    }

    public SseEmitter stream(Long userId, Integer userType, Long sessionId, String text, String agentType) {
        SseEmitter emitter = new SseEmitter(TIMEOUT_MILLIS);
        executor.execute(() -> run(emitter, userId, userType, sessionId, text, agentType));
        return emitter;
    }

    private void run(SseEmitter emitter, Long userId, Integer userType, Long sessionId,
                     String text, String agentType) {
        try {
            send(emitter, "stage", Map.of("key", "route", "label", "理解意图与装配上下文"));
            send(emitter, "stage", Map.of("key", "execute", "label", "Agent 正在调用受控工具"));
            AgentChatService.ChatResult result = chatService.chat(userId, userType, sessionId, text, agentType);
            send(emitter, "meta", Map.of(
                    "sessionId", result.sessionId(),
                    "taskId", result.taskId() == null ? "" : result.taskId()));
            send(emitter, "stage", Map.of("key", "compose", "label", "整理证据与生成答复"));
            Matcher matcher = HITL_ID.matcher(result.reply());
            if (matcher.find()) {
                send(emitter, "hitl", Map.of("requestId", matcher.group()));
            }
            emitChunks(emitter, result.reply());
            send(emitter, "done", Map.of("sessionId", result.sessionId()));
            emitter.complete();
        } catch (Exception e) {
            log.warn("Copilot stream failed userId={} sessionId={}: {}", userId, sessionId, e.getMessage());
            try {
                send(emitter, "error", Map.of("message", "AI 服务暂不可用，请稍后重试"));
                emitter.complete();
            } catch (Exception sendFailure) {
                emitter.completeWithError(sendFailure);
            }
        }
    }

    private void emitChunks(SseEmitter emitter, String text) throws IOException {
        String value = text == null ? "（无回复）" : text;
        int start = 0;
        while (start < value.length()) {
            int remaining = value.codePointCount(start, value.length());
            int end = value.offsetByCodePoints(start, Math.min(CHUNK_CODE_POINTS, remaining));
            send(emitter, "delta", Map.of("text", value.substring(start, end)));
            start = end;
        }
    }

    private void send(SseEmitter emitter, String event, Object data) throws IOException {
        emitter.send(SseEmitter.event().name(event).data(data));
    }
}
