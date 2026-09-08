package com.lms.ai.harness;

import cn.hutool.core.util.StrUtil;
import com.lms.ai.config.HarnessProperties;
import com.lms.common.exceptions.CommonException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Map;

/**
 * 会话生命周期兜底（spec GLOBAL_HARNESS_SPEC §4.5：最大 turn / 最大 token）
 *
 * - 用量收敛在 Redis hash（agent:usage:{sessionId}：turns / tokens），TTL 随会话；
 * - turns：每轮用户消息 +1；tokens：文本近似估算（中文 ~1 字符 ≈ 1 token，取 length/2 保守值）；
 * - 上限：lms.ai.harness.session.max-user-turns / max-tokens（0 = 不限）；
 * - 超限：抛 CommonException 提示终止会话并新建（配合 AgentChatService 自动结束会话）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HarnessSessionGuard {

    private static final String FIELD_TURNS = "turns";
    private static final String FIELD_TOKENS = "tokens";
    /** 写工具调用次数（P4 预算扩展：网关放行写工具前 +1） */
    public static final String FIELD_WRITES = "writes";
    /** 邀请次数（P4 预算扩展：邀请放行前 +1） */
    public static final String FIELD_INVITES = "invites";
    /** 保守 token 估算：中英混合按 2 字符 ≈ 1 token */
    private static final int TOKEN_DIVISOR = 2;

    private final StringRedisTemplate redisTemplate;
    private final HarnessProperties properties;
    private final HarnessTraceService traceService;

    /** 每轮用户消息前检查：超限抛异常（调用方应结束会话并提示新建） */
    public void checkBeforeTurn(Long sessionId) {
        HarnessProperties.Session cfg = properties.getSession();
        if (sessionId == null || (cfg.getMaxUserTurns() <= 0 && cfg.getMaxTokens() <= 0)) {
            return;
        }
        Map<Object, Object> usage = redisTemplate.opsForHash().entries(key(sessionId));
        long turns = parseLong(usage.get(FIELD_TURNS));
        long tokens = parseLong(usage.get(FIELD_TOKENS));
        if (cfg.getMaxUserTurns() > 0 && turns >= cfg.getMaxUserTurns()) {
            log.warn("[Harness] 会话达轮次上限 sessionId={} turns={} max={}", sessionId, turns, cfg.getMaxUserTurns());
            traceService.record(String.valueOf(sessionId), HarnessTraceService.EVENT_SESSION_LIMIT,
                    Map.of("field", "turns", "value", String.valueOf(turns), "max", String.valueOf(cfg.getMaxUserTurns())));
            throw new CommonException("会话已达到最大轮次限制（" + cfg.getMaxUserTurns() + " 轮），请新建会话继续提问");
        }
        if (cfg.getMaxTokens() > 0 && tokens >= cfg.getMaxTokens()) {
            log.warn("[Harness] 会话达 token 上限 sessionId={} tokens={} max={}", sessionId, tokens, cfg.getMaxTokens());
            traceService.record(String.valueOf(sessionId), HarnessTraceService.EVENT_SESSION_LIMIT,
                    Map.of("field", "tokens", "value", String.valueOf(tokens), "max", String.valueOf(cfg.getMaxTokens())));
            throw new CommonException("会话已达到 token 预算上限，请新建会话继续提问");
        }
    }

    /**
     * 记录一轮用户消息（turns+1，tokens+估算）。
     *
     * @return null=放行；非 null=超限原因（调用方据此结束会话并抛异常）
     */
    public String recordUserTurn(Long sessionId, String text) {
        HarnessProperties.Session cfg = properties.getSession();
        if (sessionId == null) {
            return null;
        }
        long tokenCost = estimateTokens(text);
        Long turns = redisTemplate.opsForHash().increment(key(sessionId), FIELD_TURNS, 1);
        Long tokens = redisTemplate.opsForHash().increment(key(sessionId), FIELD_TOKENS, tokenCost);
        redisTemplate.expire(key(sessionId), Duration.ofDays(properties.getTrace().getTtlDays()));
        if (cfg.getMaxUserTurns() > 0 && turns != null && turns > cfg.getMaxUserTurns()) {
            return "会话轮次超限（" + turns + "/" + cfg.getMaxUserTurns() + "），已终止，请新建会话";
        }
        if (cfg.getMaxTokens() > 0 && tokens != null && tokens > cfg.getMaxTokens()) {
            return "会话 token 预算超限，已终止，请新建会话";
        }
        return null;
    }

    /** 记录助手回复 token（不占用户轮次） */
    public void recordAssistant(Long sessionId, String text) {
        if (sessionId == null) {
            return;
        }
        redisTemplate.opsForHash().increment(key(sessionId), FIELD_TOKENS, estimateTokens(text));
        redisTemplate.expire(key(sessionId), Duration.ofDays(properties.getTrace().getTtlDays()));
    }

    /** 当前用量（调试/前端展示） */
    public Map<Object, Object> usage(Long sessionId) {
        return sessionId == null ? Map.of() : redisTemplate.opsForHash().entries(key(sessionId));
    }

    /**
     * 会话级动作计数（P4：写工具/邀请次数预算）。
     * 每次调用 +1；max &gt; 0 且超限时返回原因（调用方据此 DENY），否则返回 null。
     * sessionId 为字符串（可能非数字，如 eval-1 管道会话）。
     *
     * @param field HarnessSessionGuard.FIELD_WRITES / FIELD_INVITES
     */
    public String countLimited(String sessionId, String field, long max) {
        if (sessionId == null) {
            return null;
        }
        Long v = redisTemplate.opsForHash().increment(key(sessionId), field, 1L);
        redisTemplate.expire(key(sessionId), Duration.ofDays(properties.getTrace().getTtlDays()));
        if (max > 0 && v != null && v > max) {
            String label = FIELD_WRITES.equals(field) ? "写操作" : "邀请";
            return "会话" + label + "次数超限（" + v + "/" + max + "），请新建会话继续";
        }
        return null;
    }

    /** 清除会话用量（结束会话时调用） */
    public void clear(Long sessionId) {
        if (sessionId != null) {
            redisTemplate.delete(key(sessionId));
        }
    }

    private long estimateTokens(String text) {
        if (StrUtil.isBlank(text)) {
            return 0;
        }
        return Math.max(1, text.length() / TOKEN_DIVISOR);
    }

    private long parseLong(Object v) {
        if (v == null) {
            return 0;
        }
        try {
            return Long.parseLong(String.valueOf(v));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private String key(Long sessionId) {
        return HarnessKeys.USAGE_PREFIX + sessionId;
    }

    private String key(String sessionId) {
        return HarnessKeys.USAGE_PREFIX + sessionId;
    }
}
