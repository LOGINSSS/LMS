package com.lms.ai.harness;

import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import com.lms.ai.config.HarnessProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * HITL（Human-in-the-Loop）人工确认服务（spec HEAVY_HARNESS_SPEC §8.5：动作级）
 *
 * 从 v1 的"高危 agent 邀请"升级为**动作级**确认，请求/批复键：
 * - 确认请求：agent:hitl2:req:{requestId}（hash：sessionId/userId/actionType/actionId/inviter/reason/status）
 * - 批复缓存：agent:hitl2:ok|no:{sessionId}:{actionType}:{actionId}（TTL=approvalTtlSeconds）
 *   例：invite exam-agent → ok:{sessionId}:INVITE_AGENT:exam-agent
 *       teacher 直推 IM  → ok:{sessionId}:CALL_TOOL:im.pushMessage
 *
 * 安全模型不变：请求由确定性代码生成，放行必须有 Redis 人工批复记录，LLM 无法绕过。
 * 动作级语义：确认针对"具体高危动作"，批复后同会话同动作窗口内免重复确认。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HitlService {

    private final StringRedisTemplate redisTemplate;
    private final HarnessProperties properties;

    /**
     * 创建确认请求（动作级），返回 requestId。
     *
     * @param sessionId  会话 id（空 → 返回 null，调用方 fail-closed 拒绝）
     * @param userId     动作归属用户
     * @param actionType 动作类型（INVITE_AGENT / CALL_TOOL / SCHEDULE_TASK）
     * @param actionId   动作目标（targetAgent 或 toolId）
     * @param inviter    发起 agent（可为空）
     * @param reason     展示给用户/前端的原因
     */
    public String create(String sessionId, Long userId, ActionType actionType, String actionId,
                         String inviter, String reason) {
        if (StrUtil.isBlank(sessionId) || !properties.getHitl().isEnabled() || actionType == null
                || StrUtil.isBlank(actionId)) {
            return null;
        }
        String requestId = "hitl-" + IdUtil.fastSimpleUUID();
        Map<String, String> req = new HashMap<>();
        req.put("sessionId", sessionId);
        req.put("userId", userId == null ? "" : String.valueOf(userId));
        req.put("actionType", actionType.name());
        req.put("actionId", actionId);
        req.put("inviter", StrUtil.blankToDefault(inviter, ""));
        req.put("reason", StrUtil.blankToDefault(reason, properties.getHitl().getHint()));
        req.put("status", String.valueOf(HarnessKeys.HITL_PENDING));
        req.put("createTime", String.valueOf(System.currentTimeMillis()));
        redisTemplate.opsForHash().putAll(reqKey(requestId), req);
        redisTemplate.expire(reqKey(requestId), Duration.ofSeconds(properties.getHitl().getRequestTtlSeconds()));
        log.info("[Harness2-HITL] 确认请求已创建 requestId={} sessionId={} action={}:{} inviter={}",
                requestId, sessionId, actionType, actionId, inviter);
        return requestId;
    }

    /** 查询请求详情（approve/reject 前端展示用） */
    public Map<Object, Object> detail(String requestId) {
        if (StrUtil.isBlank(requestId)) {
            return Map.of();
        }
        return redisTemplate.opsForHash().entries(reqKey(requestId));
    }

    /** 人工批准：写 ok 缓存（同会话同动作窗口内免重复确认） */
    public boolean approve(String requestId) {
        Map<Object, Object> req = detail(requestId);
        if (req.isEmpty()) {
            return false;
        }
        String sessionId = String.valueOf(req.get("sessionId"));
        String actionType = String.valueOf(req.getOrDefault("actionType", ActionType.INVITE_AGENT.name()));
        String actionId = String.valueOf(req.getOrDefault("actionId", req.get("target")));
        redisTemplate.opsForHash().put(reqKey(requestId), "status", String.valueOf(HarnessKeys.HITL_APPROVED));
        redisTemplate.expire(reqKey(requestId), Duration.ofSeconds(properties.getHitl().getRequestTtlSeconds()));
        redisTemplate.delete(noKey(sessionId, actionType, actionId));
        redisTemplate.opsForValue().set(okKey(sessionId, actionType, actionId), "1",
                Duration.ofSeconds(properties.getHitl().getApprovalTtlSeconds()));
        log.info("[Harness2-HITL] 请求已批准 requestId={} sessionId={} action={}:{}",
                requestId, sessionId, actionType, actionId);
        return true;
    }

    /** 人工拒绝：写 no 缓存（窗口内同动作不再询问，直接拒绝） */
    public boolean reject(String requestId) {
        Map<Object, Object> req = detail(requestId);
        if (req.isEmpty()) {
            return false;
        }
        String sessionId = String.valueOf(req.get("sessionId"));
        String actionType = String.valueOf(req.getOrDefault("actionType", ActionType.INVITE_AGENT.name()));
        String actionId = String.valueOf(req.getOrDefault("actionId", req.get("target")));
        redisTemplate.opsForHash().put(reqKey(requestId), "status", String.valueOf(HarnessKeys.HITL_REJECTED));
        redisTemplate.expire(reqKey(requestId), Duration.ofSeconds(properties.getHitl().getRequestTtlSeconds()));
        redisTemplate.opsForValue().set(noKey(sessionId, actionType, actionId), "1",
                Duration.ofSeconds(properties.getHitl().getApprovalTtlSeconds()));
        log.info("[Harness2-HITL] 请求被拒绝 requestId={} sessionId={} action={}:{}",
                requestId, sessionId, actionType, actionId);
        return true;
    }

    /** 会话维度：该动作是否已有有效批准（窗口内免确认） */
    public boolean isApproved(String sessionId, ActionType actionType, String actionId) {
        return StrUtil.isNotBlank(sessionId) && actionType != null
                && Boolean.TRUE.equals(redisTemplate.hasKey(okKey(sessionId, actionType.name(), actionId)));
    }

    /** 会话维度：该动作是否已被人工拒绝（窗口内直接拒绝） */
    public boolean isRejected(String sessionId, ActionType actionType, String actionId) {
        return StrUtil.isNotBlank(sessionId) && actionType != null
                && Boolean.TRUE.equals(redisTemplate.hasKey(noKey(sessionId, actionType.name(), actionId)));
    }

    // ---------- keys ----------

    private String reqKey(String requestId) {
        return HarnessKeys.HITL2_REQ_PREFIX + requestId;
    }

    private String okKey(String sessionId, String actionType, String actionId) {
        return HarnessKeys.HITL2_OK_PREFIX + sessionId + ":" + actionType + ":" + actionId;
    }

    private String noKey(String sessionId, String actionType, String actionId) {
        return HarnessKeys.HITL2_NO_PREFIX + sessionId + ":" + actionType + ":" + actionId;
    }
}
