package com.lms.ai.session;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lms.ai.config.AiAgentProperties;
import com.lms.ai.harness.HarnessSessionGuard;
import com.lms.common.exceptions.CommonException;
import com.lms.common.utils.JsonUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * L1 会话记忆服务（spec §4.1/§4.2）
 *
 * - 元数据：MySQL agent_session（我的会话列表 / 结束会话）
 * - 消息：Redis `agent:session:{sessionId}:messages`（TTL 1~7 天），多轮消息按 (userId, sessionId) 隔离
 * - 上下文裁剪：超长时保留「系统 + 画像摘要 + 最近 N 轮」（阶段 5 完整实现，当前按条数裁剪）
 * - Harness（GLOBAL_HARNESS_SPEC §4.5）：结束会话时清理会话用量计数
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgentSessionService {

    private static final String MSG_KEY_PREFIX = "agent:session:";
    private static final String MSG_KEY_SUFFIX = ":messages";
    /** 裁剪阈值：超过则丢弃最旧的一半（练手实现，阶段 5 升级为 LLM 摘要压缩） */
    private static final int MAX_MESSAGES = 40;

    private final AgentSessionMapper sessionMapper;
    private final StringRedisTemplate redisTemplate;
    private final AiAgentProperties properties;
    private final HarnessSessionGuard sessionGuard;

    /** 建会话 */
    public AgentSession createSession(Long userId, String agentType, String title) {
        AgentSession s = new AgentSession();
        s.setUserId(userId);
        s.setAgentType(agentType);
        s.setTitle(StrUtil.blankToDefault(title, "新会话"));
        s.setStatus(AgentSession.STATUS_ACTIVE);
        s.setMessageCount(0);
        sessionMapper.insert(s);
        return s;
    }

    /** 我的会话列表（进行中优先） */
    public List<AgentSession> listMine(Long userId) {
        return sessionMapper.selectList(new LambdaQueryWrapper<AgentSession>()
                .eq(AgentSession::getUserId, userId)
                .orderByDesc(AgentSession::getStatus)
                .orderByDesc(AgentSession::getId));
    }

    /** 取会话并校验归属 */
    public AgentSession getOwned(Long userId, Long sessionId) {
        AgentSession s = sessionMapper.selectById(sessionId);
        if (s == null || !Objects.equals(s.getUserId(), userId)) {
            throw new CommonException("会话不存在或无权访问");
        }
        return s;
    }

    /** 结束会话：L1 摘要压缩 → 追加进 L2 行为流水（spec §4.5 第 5 步）+ 清理 Harness 用量 */
    public void closeSession(Long userId, Long sessionId) {
        AgentSession s = getOwned(userId, sessionId);
        if (s.getStatus() == AgentSession.STATUS_CLOSED) {
            return;
        }
        // L1 消息 → L2 行为流水（会话关闭事件；画像摘要压缩由 ProfileService 每 N 条事件/每日触发）
        s.setStatus(AgentSession.STATUS_CLOSED);
        sessionMapper.updateById(s);
        redisTemplate.delete(msgKey(sessionId));
        clearSummary(sessionId);
        sessionGuard.clear(sessionId);
    }

    /** 追加一条消息（user/assistant）并更新计数 */
    public void appendMessage(Long sessionId, String role, String text) {
        String key = msgKey(sessionId);
        Long size = redisTemplate.opsForList().size(key);
        if (size != null && size >= MAX_MESSAGES) {
            // 简单裁剪：丢最旧一半（阶段 5：LLM 压缩进 L2 summary）
            trimHalf(key);
        }
        String json = JsonUtils.toJsonStr(Map.of("role", role, "text", text, "time", System.currentTimeMillis()));
        redisTemplate.opsForList().rightPush(key, json);
        redisTemplate.expire(key, Duration.ofDays(properties.getSessionTtlDays()));
        sessionMapper.update(null, new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<AgentSession>()
                .eq(AgentSession::getId, sessionId)
                .set(AgentSession::getMessageCount, (size == null ? 0 : size) + 1));
    }

    /** 加载会话消息列表（[{role,text}]）；若有历史摘要则置顶一条 assistant 摘要 */
    public List<Map<String, String>> loadMessages(Long sessionId) {
        List<Map<String, String>> out = new ArrayList<>();
        String summary = getSummary(sessionId);
        if (summary != null && !summary.isBlank()) {
            out.add(Map.of("role", "assistant", "text", "【会话历史摘要】" + summary));
        }
        out.addAll(loadMessagesRaw(sessionId));
        return out;
    }

    /** 原始消息列表（不含摘要行；ContextCompressor 压缩判定用） */
    public List<Map<String, String>> loadMessagesRaw(Long sessionId) {
        List<Map<String, String>> out = new ArrayList<>();
        if (sessionId == null) {
            return out;
        }
        List<String> jsons = redisTemplate.opsForList().range(msgKey(sessionId), 0, -1);
        if (jsons == null) {
            return out;
        }
        for (String j : jsons) {
            try {
                cn.hutool.json.JSONObject obj = JsonUtils.parseObj(j);
                out.add(Map.of("role", obj.getStr("role"), "text", obj.getStr("text")));
            } catch (Exception e) {
                log.warn("会话消息解析失败 sessionId={}", sessionId);
            }
        }
        return out;
    }

    // ---------- P4 LLM 摘要压缩（HEAVY_HARNESS_SPEC §5.5：摘要入 Redis key，超长裁剪由 ContextCompressor 驱动） ----------

    /** 读会话历史摘要（无则 null） */
    public String getSummary(Long sessionId) {
        if (sessionId == null) {
            return null;
        }
        return redisTemplate.opsForValue().get(summaryKey(sessionId));
    }

    /** 写会话历史摘要（TTL 同消息） */
    public void putSummary(Long sessionId, String summary) {
        if (sessionId == null || summary == null || summary.isBlank()) {
            return;
        }
        redisTemplate.opsForValue().set(summaryKey(sessionId), summary,
                Duration.ofDays(properties.getSessionTtlDays()));
    }

    /** 清摘要（结束会话时一并） */
    public void clearSummary(Long sessionId) {
        if (sessionId != null) {
            redisTemplate.delete(summaryKey(sessionId));
        }
    }

    /** 只保留最近 keep 条消息（ContextCompressor 压缩后调用；keep<=0 不操作） */
    public void trimToKeep(Long sessionId, int keep) {
        if (sessionId == null || keep <= 0) {
            return;
        }
        redisTemplate.opsForList().trim(msgKey(sessionId), -keep, -1);
    }

    private void trimHalf(String key) {
        Long size = redisTemplate.opsForList().size(key);
        if (size != null && size > 0) {
            redisTemplate.opsForList().trim(key, size / 2, -1);
        }
    }

    private String msgKey(Long sessionId) {
        return MSG_KEY_PREFIX + sessionId + MSG_KEY_SUFFIX;
    }

    private String summaryKey(Long sessionId) {
        return MSG_KEY_PREFIX + sessionId + ":summary";
    }
}
