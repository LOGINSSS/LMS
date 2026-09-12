package com.lms.ai.infrastructure.memory;

import com.lms.ai.application.port.out.SessionMemoryPort;
import com.lms.ai.config.ReMeProperties;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ReMeSessionMemoryAdapterTest {

    @Test
    void enabledAdapterMapsApplicationMessagesToAutoMemory() {
        ReMeProperties properties = new ReMeProperties();
        properties.setEnabled(true);
        properties.setEndpointTemplate("http://reme.internal/users/{userId}");
        CapturingClient client = new CapturingClient();
        ReMeSessionMemoryAdapter adapter = new ReMeSessionMemoryAdapter(properties, client);

        adapter.capture(new SessionMemoryPort.CaptureRequest(7L, "lms-agent-99", List.of(
                new SessionMemoryPort.SourceMessage("user", "偏好示例", "2026-09-11T12:00:00Z"))));

        assertThat(client.userId).isEqualTo(7L);
        assertThat(client.sessionId).isEqualTo("lms-agent-99");
        assertThat(client.messages).containsExactly(
                new ReMeJobClient.SourceMessage("user", "偏好示例", "2026-09-11T12:00:00Z"));
        assertThat(client.hint).contains("学习偏好").contains("不要记录密码");
    }

    @Test
    void disabledAdapterDoesNotPersistConversation() {
        ReMeProperties properties = new ReMeProperties();
        CapturingClient client = new CapturingClient();

        new ReMeSessionMemoryAdapter(properties, client).capture(
                new SessionMemoryPort.CaptureRequest(7L, "lms-agent-99", List.of(
                        new SessionMemoryPort.SourceMessage("user", "hello", null))));

        assertThat(client.sessionId).isNull();
    }

    private static final class CapturingClient implements ReMeJobClient {
        private Long userId;
        private String sessionId;
        private List<SourceMessage> messages;
        private String hint;

        @Override
        public String search(Long userId, String query, int limit) { return null; }

        @Override
        public void write(Long userId, String path, String name, String description, String content) { }

        @Override
        public void edit(Long userId, String path, String oldText, String newText) { }

        @Override
        public void delete(Long userId, String path) { }

        @Override
        public void autoMemory(Long userId, String sessionId, List<SourceMessage> messages, String memoryHint) {
            this.userId = userId;
            this.sessionId = sessionId;
            this.messages = messages;
            this.hint = memoryHint;
        }
    }
}
