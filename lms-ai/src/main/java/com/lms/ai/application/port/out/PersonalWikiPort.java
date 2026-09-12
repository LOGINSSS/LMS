package com.lms.ai.application.port.out;

/** Application-owned commands for a user's durable, editable personal Wiki. */
public interface PersonalWikiPort {

    MemoryRef remember(RememberCommand command);

    void correct(CorrectCommand command);

    void forget(ForgetCommand command);

    record RememberCommand(Long userId, String title, String content, String description) {
    }

    record CorrectCommand(Long userId, String memoryId, String oldText, String newText) {
    }

    record ForgetCommand(Long userId, String memoryId) {
    }

    record MemoryRef(String memoryId) {
    }
}
