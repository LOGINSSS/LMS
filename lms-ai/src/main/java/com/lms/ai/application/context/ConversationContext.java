package com.lms.ai.application.context;

import com.lms.ai.application.port.out.AgentInvocation;

import java.util.List;

/** Session history and personal memory prepared for one Agent invocation. */
public record ConversationContext(
        List<AgentInvocation.Message> messages,
        String longTermMemoryContext) {

    public ConversationContext {
        messages = messages == null ? List.of() : List.copyOf(messages);
    }
}
