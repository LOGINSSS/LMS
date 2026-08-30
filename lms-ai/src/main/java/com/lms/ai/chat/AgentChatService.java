package com.lms.ai.chat;

import com.lms.ai.factory.PersonalAgentFactory;
import com.lms.ai.memory.ProfileService;
import com.lms.ai.session.AgentSession;
import com.lms.ai.session.AgentSessionService;
import com.lms.ai.tools.ToolSupport;
import com.lms.common.exceptions.CommonException;
import io.agentscope.core.ReActAgent;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.message.AssistantMessage;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.UserMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 个人 Agent 对话服务（spec §8.1：/agent/chat 多轮对话）
 *
 * L1 会话记忆：消息存 Redis（AgentSessionService），每次调用重建 agent 并传入完整历史
 * （系统 + 画像 + 最近 N 轮，spec §4.2 上下文裁剪由 AgentSessionService 控制）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgentChatService {

    private final PersonalAgentFactory personalAgentFactory;
    private final AgentSessionService sessionService;
    private final ProfileService profileService;

    /**
     * 多轮对话
     *
     * @param userId    当前用户
     * @param userType  1 学生 / 2 老师
     * @param sessionId 会话 id（null 则自动建会话）
     * @param text      用户输入
     * @param agentType student-agent / teacher-agent
     * @return 回复结果
     */
    public ChatResult chat(Long userId, Integer userType, Long sessionId, String text, String agentType) {
        AgentSession session = sessionId == null
                ? sessionService.createSession(userId, agentType, defaultTitle(text))
                : sessionService.getOwned(userId, sessionId);
        if (session.getStatus() == AgentSession.STATUS_CLOSED) {
            throw new CommonException("会话已结束，请新建会话");
        }

        // 记录行为事件（L2 原料，spec §4.3：对话/提问）
        profileService.recordBehavior(userId, ProfileService.EVENT_CHAT, Map.of("question", text, "agentType", agentType));

        // L1：组装历史 + 新消息
        List<Msg> messages = new ArrayList<>();
        for (Map<String, String> m : sessionService.loadMessages(session.getId())) {
            if ("user".equals(m.get("role"))) {
                messages.add(new UserMessage(m.get("text")));
            } else {
                messages.add(new AssistantMessage(m.get("text")));
            }
        }
        messages.add(new UserMessage(text));
        sessionService.appendMessage(session.getId(), "user", text);

        // 构建 agent + 运行上下文（用户身份经 RuntimeContext 注入工具，spec §5.3）
        ReActAgent agent = personalAgentFactory.build(userId, userType, agentType);
        RuntimeContext ctx = RuntimeContext.builder()
                .sessionId(String.valueOf(session.getId()))
                .userId(String.valueOf(userId))
                .put(ToolSupport.CTX_USER_ID, userId)
                .put(ToolSupport.CTX_USER_TYPE, userType)
                .build();

        String reply;
        try {
            Msg result = agent.call(messages, ctx).block();
            reply = result == null ? "（无回复）" : result.getTextContent();
        } catch (Exception e) {
            log.error("agent 调用失败 userId={} sessionId={}", userId, session.getId(), e);
            throw new CommonException("AI 服务调用失败: " + e.getMessage());
        } finally {
            agent.close();
        }
        sessionService.appendMessage(session.getId(), "assistant", reply);
        return new ChatResult(session.getId(), reply, null);
    }

    /**
     * IM 回调回流：把老师 IM 回复投递回 teacher-agent 会话，组织答案（spec §7.3【回】）
     */
    public String deliverImReply(Long sessionId, Long teacherId, String replyText) {
        AgentSession session = sessionService.getOwned(teacherId, sessionId);
        ReActAgent agent = personalAgentFactory.build(teacherId, 2, session.getAgentType());
        RuntimeContext ctx = RuntimeContext.builder()
                .sessionId(String.valueOf(session.getId()))
                .userId(String.valueOf(teacherId))
                .put(ToolSupport.CTX_USER_ID, teacherId)
                .put(ToolSupport.CTX_USER_TYPE, 2)
                .build();
        String prompt = "【IM 回流】老师在 IM 小助手中回复了学生的问题，请把回复组织成给学生看的答案（保留原意，补充必要上下文）：\n"
                + replyText;
        try {
            Msg result = agent.call(List.of(new UserMessage(prompt)), ctx).block();
            String answer = result == null ? replyText : result.getTextContent();
            sessionService.appendMessage(session.getId(), "user", prompt);
            sessionService.appendMessage(session.getId(), "assistant", answer);
            return answer;
        } catch (Exception e) {
            log.error("IM 回流处理失败 sessionId={}", sessionId, e);
            return replyText;
        } finally {
            agent.close();
        }
    }

    /** 会话默认标题：取问题前 20 字 */
    private String defaultTitle(String text) {
        String t = text == null ? "" : text.strip();
        return t.length() <= 20 ? t : t.substring(0, 20) + "...";
    }

    /** 对话结果 */
    public record ChatResult(Long sessionId, String reply, String taskId) {
    }
}
