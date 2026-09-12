package com.lms.ai.application.context;

/** Input required to assemble one personal-Agent conversation context. */
public record ContextAssemblyRequest(
        Long userId,
        Integer userType,
        Long sessionId,
        String agentType,
        String userText) {
}
