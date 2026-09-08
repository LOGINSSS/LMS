package com.lms.ai.harness;

import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * 管道令牌（spec HEAVY_HARNESS_SPEC §8.5：服务端签发 pipelineToken，管道内写回流免动作级确认）
 *
 * 语义：绑定 (sessionId, userId, flow) 的短期令牌，由服务端代码签发（LearningPipelineService/编排层），
 * 经 RuntimeContext（CTX_PIPELINE_TOKEN）透传给管道内节点 Agent 的工具调用；
 * PolicyEngine 见到有效令牌 → 直接 ALLOW（身份仍限本人，令牌本身绑定 userId）。
 * 防止 LLM 自行伪造豁免：令牌随机、存 Redis、短 TTL、一次性语义（校验后即删，仅限单次动作）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PipelineTokenService {

    /** 令牌有效期（秒） */
    private static final long TTL_SECONDS = 900;

    private final StringRedisTemplate redisTemplate;

    /**
     * 签发一次性管道令牌
     *
     * @param sessionId 会话
     * @param userId    归属用户（令牌只对该用户有效）
     * @param flow      业务流标识（如 assess / exercise / diagnose）
     */
    public String issue(String sessionId, Long userId, String flow) {
        if (StrUtil.isBlank(sessionId) || userId == null) {
            return null;
        }
        String token = "pt-" + IdUtil.fastSimpleUUID();
        redisTemplate.opsForValue().set(key(token), sessionId + ":" + userId + ":" + StrUtil.nullToEmpty(flow),
                Duration.ofSeconds(TTL_SECONDS));
        return token;
    }

    /**
     * 校验并消费（一次性）：令牌存在、绑定关系匹配 → 删除并放行
     */
    public boolean validateAndConsume(String token, String sessionId, Long userId) {
        if (StrUtil.isBlank(token) || StrUtil.isBlank(sessionId) || userId == null) {
            return false;
        }
        try {
            String v = redisTemplate.opsForValue().get(key(token));
            if (v == null) {
                return false;
            }
            String[] parts = v.split(":", 3);
            boolean ok = parts.length >= 2
                    && parts[0].equals(sessionId)
                    && parts[1].equals(String.valueOf(userId));
            if (ok) {
                redisTemplate.delete(key(token)); // 一次性
            }
            return ok;
        } catch (Exception e) {
            log.warn("pipelineToken 校验异常（fail-closed 拒绝）: {}", e.getMessage());
            return false;
        }
    }

    private String key(String token) {
        return HarnessKeys.PIPELINE_TOKEN_PREFIX + token;
    }
}
