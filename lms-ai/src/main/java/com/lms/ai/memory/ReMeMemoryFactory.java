package com.lms.ai.memory;

import cn.hutool.core.util.StrUtil;
import com.lms.ai.config.ReMeProperties;
import io.agentscope.core.memory.LongTermMemory;
import io.agentscope.core.memory.reme.ReMeLongTermMemory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * ReMe 长期记忆工厂（spec §4.3：L2 画像/习惯记忆的 AgentScope ReMe 实现）
 *
 * - 启用（lms.ai.agent.reme.enabled=true 且配置 base-url）→ ReMeLongTermMemory：
 *   对话轨迹自动提取记忆（record），回答前检索注入（retrieve），workspaceId = user_{userId}
 *   一人一个记忆空间，正好对应"每人一个 agent"（spec §1.2）
 * - 未启用/未配置 → 回退 ProfileLongTermMemory（MySQL 画像，保留结构化画像能力）
 * 实例按 userId 缓存（ReMeLongTermMemory 内含 HTTP client，避免每次对话重建）
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReMeMemoryFactory {

    private final ReMeProperties properties;
    private final ProfileLongTermMemory fallback;

    private final Map<Long, LongTermMemory> cache = new ConcurrentHashMap<>();

    public LongTermMemory get(Long userId) {
        if (!properties.isEnabled() || StrUtil.isBlank(properties.getBaseUrl())) {
            return fallback;
        }
        return cache.computeIfAbsent(userId, uid -> {
            ReMeLongTermMemory memory = ReMeLongTermMemory.builder()
                    .userId(properties.getWorkspacePrefix() + uid)
                    .apiBaseUrl(properties.getBaseUrl())
                    .timeout(Duration.ofMillis(properties.getTimeoutMs()))
                    .build();
            log.info("启用 ReMe 长期记忆 workspace={}{}", properties.getWorkspacePrefix(), uid);
            return memory;
        });
    }
}
