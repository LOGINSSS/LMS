package com.lms.ai.application.context;

import com.lms.ai.application.port.out.AgentInvocation;
import com.lms.ai.application.port.out.LongTermMemoryPort;
import com.lms.ai.session.AgentSessionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/** Assembles short-term session history and recalled personal memory for a conversation turn. */
@Slf4j
@Service
@RequiredArgsConstructor
public class ContextAssembler {

    private static final int DEFAULT_MEMORY_LIMIT = 5;

    private final AgentSessionService sessionService;
    private final ContextCompressor contextCompressor;
    private final LongTermMemoryPort longTermMemoryPort;

    public ConversationContext assemble(ContextAssemblyRequest request) {
        compressBestEffort(request.sessionId());

        List<AgentInvocation.Message> messages = new ArrayList<>();
        for (Map<String, String> message : sessionService.loadMessages(request.sessionId())) {
            if ("user".equals(message.get("role"))) {
                messages.add(AgentInvocation.Message.user(message.get("text")));
            } else {
                messages.add(AgentInvocation.Message.assistant(message.get("text")));
            }
        }
        messages.add(AgentInvocation.Message.user(request.userText()));

        return new ConversationContext(messages, recallLongTermMemory(request));
    }

    /** Recalls personal memory without loading session history, for non-chat invocation paths such as IM callbacks. */
    public String recallLongTermMemory(ContextAssemblyRequest request) {
        return recallBestEffort(request);
    }

    private void compressBestEffort(Long sessionId) {
        try {
            contextCompressor.compressIfNeeded(sessionId);
        } catch (Exception e) {
            log.warn("上下文压缩执行失败（忽略）sessionId={}: {}", sessionId, e.getMessage());
        }
    }

    private String recallBestEffort(ContextAssemblyRequest request) {
        try {
            List<LongTermMemoryPort.MemoryFragment> fragments = longTermMemoryPort.recall(
                    new LongTermMemoryPort.RecallRequest(
                            request.userId(), request.userType(), request.agentType(),
                            request.userText(), DEFAULT_MEMORY_LIMIT));
            if (fragments == null) {
                return null;
            }
            String context = fragments.stream()
                    .filter(Objects::nonNull)
                    .map(LongTermMemoryPort.MemoryFragment::content)
                    .filter(content -> content != null && !content.isBlank())
                    .limit(DEFAULT_MEMORY_LIMIT)
                    .collect(Collectors.joining("\n"));
            return context.isBlank() ? null : context;
        } catch (Exception e) {
            log.warn("长期记忆召回失败（忽略）userId={}: {}", request.userId(), e.getMessage());
            return null;
        }
    }
}
