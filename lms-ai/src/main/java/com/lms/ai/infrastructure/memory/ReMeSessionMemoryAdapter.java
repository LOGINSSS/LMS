package com.lms.ai.infrastructure.memory;

import com.lms.ai.application.port.out.SessionMemoryPort;
import com.lms.ai.config.ReMeProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** ReMe auto_memory adapter for source conversations captured at session close. */
@Component
@RequiredArgsConstructor
public class ReMeSessionMemoryAdapter implements SessionMemoryPort {

    private static final String MEMORY_HINT = "仅提取可长期复用的学习偏好、目标、关键决定和进度；"
            + "不要记录密码、令牌、联系方式或其他敏感个人信息。";

    private final ReMeProperties properties;
    private final ReMeJobClient reme;

    @Override
    public void capture(CaptureRequest request) {
        if (!properties.isEnabled() || request.messages() == null || request.messages().isEmpty()) {
            return;
        }
        reme.autoMemory(request.userId(), request.sessionId(), request.messages().stream()
                .map(message -> new ReMeJobClient.SourceMessage(
                        message.role(), message.content(), message.createdAt()))
                .toList(), MEMORY_HINT);
    }
}
