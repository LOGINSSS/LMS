package com.lms.ai.infrastructure.memory;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/** Narrow client for the allowlisted ReMe HTTP Jobs used by LMS. */
public interface ReMeJobClient {

    String search(Long userId, String query, int limit);

    void write(Long userId, String path, String name, String description, String content);

    void edit(Long userId, String path, String oldText, String newText);

    void delete(Long userId, String path);

    void autoMemory(Long userId, String sessionId, List<SourceMessage> messages, String memoryHint);

    record SourceMessage(String role, String content, @JsonProperty("created_at") String createdAt) {
    }
}
