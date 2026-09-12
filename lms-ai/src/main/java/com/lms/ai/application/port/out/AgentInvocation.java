package com.lms.ai.application.port.out;

import java.util.List;

/** Application-owned input for one personal-Agent invocation. */
public record AgentInvocation(
        Long userId,
        Integer userType,
        Long sessionId,
        String agentType,
        String intent,
        String intentHint,
        String longTermMemoryContext,
        List<Message> messages) {

    public AgentInvocation {
        messages = messages == null ? List.of() : List.copyOf(messages);
    }

    public record Message(Role role, String text) {

        public static Message user(String text) {
            return new Message(Role.USER, text);
        }

        public static Message assistant(String text) {
            return new Message(Role.ASSISTANT, text);
        }
    }

    public enum Role {
        USER,
        ASSISTANT
    }
}
