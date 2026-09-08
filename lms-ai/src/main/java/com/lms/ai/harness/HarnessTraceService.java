package com.lms.ai.harness;

import cn.hutool.core.util.StrUtil;
import com.lms.ai.config.HarnessProperties;
import com.lms.common.utils.JsonUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Harness Trace 统一埋点（spec GLOBAL_HARNESS_SPEC §4.4）
 *
 * 记录：谁发起邀请、邀请哪个 Agent、决策与原因、HITL 结果、会话生命周期事件。
 * 每会话 Redis 环形缓冲（List，保留 recentLimit 条，TTL ttlDays），按时间序查询；
 * 同时落结构化日志便于链路检索。专家 Agent 无感知（拦截在 GuardedAgentTool 统一埋点）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HarnessTraceService {

    /** 事件类型 */
    public static final String EVENT_INVITE = "invite";
    public static final String EVENT_TOOL = "tool_call";
    public static final String EVENT_INTENT = "intent_route";
    public static final String EVENT_HITL_CREATE = "hitl_create";
    public static final String EVENT_HITL_APPROVE = "hitl_approve";
    public static final String EVENT_HITL_REJECT = "hitl_reject";
    public static final String EVENT_SESSION_LIMIT = "session_limit";
    public static final String EVENT_SESSION_TERMINATED = "session_terminated";

    private final StringRedisTemplate redisTemplate;
    private final HarnessProperties properties;

    /** 记录一条 Trace（sessionId 为空时只落日志，不入 Redis） */
    public void record(String sessionId, String eventType, Map<String, Object> fields) {
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("time", System.currentTimeMillis());
        entry.put("event", eventType);
        if (fields != null) {
            entry.putAll(fields);
        }
        String line = JsonUtils.toJsonStr(entry);
        log.info("[Harness-Trace] sessionId={} event={} {}", sessionId == null ? "-" : sessionId, eventType, line);
        if (!properties.isEnabled() || !properties.getTrace().isEnabled() || StrUtil.isBlank(sessionId)) {
            return;
        }
        String key = HarnessKeys.TRACE_PREFIX + sessionId;
        try {
            Long size = redisTemplate.opsForList().leftPush(key, line);
            if (size != null && size > properties.getTrace().getRecentLimit()) {
                redisTemplate.opsForList().trim(key, 0, properties.getTrace().getRecentLimit() - 1);
            }
            redisTemplate.expire(key, Duration.ofDays(properties.getTrace().getTtlDays()));
        } catch (Exception e) {
            log.warn("Trace 入 Redis 失败 sessionId={}: {}", sessionId, e.getMessage());
        }
    }

    /** 会话内最近 N 条 Trace（新→旧） */
    public List<Map<String, Object>> recent(String sessionId, int limit) {
        List<Map<String, Object>> out = new ArrayList<>();
        if (StrUtil.isBlank(sessionId)) {
            return out;
        }
        int n = limit <= 0 ? properties.getTrace().getRecentLimit() : Math.min(limit, 200);
        List<String> lines = redisTemplate.opsForList().range(HarnessKeys.TRACE_PREFIX + sessionId, 0, n - 1);
        if (lines != null) {
            for (String l : lines) {
                try {
                    out.add(JsonUtils.parseObj(l));
                } catch (Exception e) {
                    out.add(Map.of("raw", l));
                }
            }
        }
        return out;
    }
}
