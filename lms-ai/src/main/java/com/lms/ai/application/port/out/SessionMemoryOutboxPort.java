package com.lms.ai.application.port.out;

/** Durable application boundary for scheduling a session-memory capture. */
@FunctionalInterface
public interface SessionMemoryOutboxPort {

    void enqueue(SessionMemoryPort.CaptureRequest request);
}
