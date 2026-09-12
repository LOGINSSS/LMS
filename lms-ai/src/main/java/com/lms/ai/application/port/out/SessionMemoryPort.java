package com.lms.ai.application.port.out;

import java.util.List;

/** Application boundary for turning a completed conversation into durable memory. */
@FunctionalInterface
public interface SessionMemoryPort {

    void capture(CaptureRequest request);

    record CaptureRequest(Long userId, String sessionId, List<SourceMessage> messages) {
    }

    record SourceMessage(String role, String content, String createdAt) {
    }
}
