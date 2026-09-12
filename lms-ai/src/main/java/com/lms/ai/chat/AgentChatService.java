package com.lms.ai.chat;

import com.lms.ai.application.context.ContextAssembler;
import com.lms.ai.application.context.ContextAssemblyRequest;
import com.lms.ai.application.context.ConversationContext;
import com.lms.ai.application.port.out.AgentInvocation;
import com.lms.ai.application.port.out.AgentInvocationResult;
import com.lms.ai.application.port.out.AgentRuntime;
import com.lms.ai.application.memory.SessionClosureService;
import com.lms.ai.harness.HarnessSessionGuard;
import com.lms.ai.harness.HarnessTraceService;
import com.lms.ai.intent.IntentDecision;
import com.lms.ai.intent.IntentRouterService;
import com.lms.ai.memory.ProfileService;
import com.lms.ai.session.AgentSession;
import com.lms.ai.session.AgentSessionService;
import com.lms.ai.task.TaskService;
import com.lms.common.exceptions.CommonException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * 个人 Agent 对话服务（spec §8.1：/agent/chat 多轮对话）
 *
 * L1 会话记忆：消息存 Redis（AgentSessionService），每次调用重建 agent 并传入完整历史
 * （系统 + 画像 + 最近 N 轮，spec §4.2 上下文裁剪由 AgentSessionService 控制）。
 *
 * Harness（GLOBAL_HARNESS_SPEC）：每轮对话前做会话生命周期检查（turn/token 预算），
 * 超限自动结束会话；对话后记录用量与 Trace。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgentChatService {

    private final AgentRuntime agentRuntime;
    private final AgentSessionService sessionService;
    private final ProfileService profileService;
    private final HarnessSessionGuard sessionGuard;
    private final IntentRouterService intentRouter;
    private final HarnessTraceService traceService;
    private final TaskService taskService;
    private final ContextAssembler contextAssembler;
    private final SessionClosureService sessionClosureService;

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

        // Harness：会话生命周期检查（超限抛异常并提示新建）
        sessionGuard.checkBeforeTurn(session.getId());
        // Harness：记录本轮用户用量（超限返回原因 → 结束会话）
        String limitReason = sessionGuard.recordUserTurn(session.getId(), text);
        if (limitReason != null) {
            sessionClosureService.close(userId, session.getId());
            throw new CommonException(limitReason);
        }

        // 记录行为事件（L2 原料，spec §4.3：对话/提问）
        profileService.recordBehavior(userId, ProfileService.EVENT_CHAT, Map.of("question", text, "agentType", agentType));

        // Context：统一装配短期历史、摘要与个人长期记忆；持久化仍由 Session/Memory 各自负责。
        ConversationContext context = contextAssembler.assemble(new ContextAssemblyRequest(
                userId, userType, session.getId(), agentType, text));
        sessionService.appendMessage(session.getId(), "user", text);

        // L0 意图路由（确定性规则层，spec HEAVY_HARNESS_SPEC §4）：写 ctx 供策略/flow 判定 + Trace 度量；
        // UNKNOWN = 自由 ReAct 兜底（不阻断对话，只记录）
        IntentDecision intentDecision = intentRouter.route(userId, userType, session.getId(), text, agentType);

        traceService.record(String.valueOf(session.getId()), HarnessTraceService.EVENT_INTENT, Map.of(
                "agent", agentType,
                "intent", intentDecision.intent().name(),
                "confidence", String.valueOf(intentDecision.confidence()),
                "source", intentDecision.source(),
                "textLen", String.valueOf(text == null ? 0 : text.length())));

        String reply;
        try {
            AgentInvocationResult result = agentRuntime.invoke(new AgentInvocation(
                    userId,
                    userType,
                    session.getId(),
                    agentType,
                    intentDecision.isKnown() ? intentDecision.intent().name() : null,
                    intentDecision.isKnown() ? intentDecision.hint() : null,
                    context.longTermMemoryContext(),
                    context.messages()));
            reply = result == null || result.reply() == null ? "（无回复）" : result.reply();
        } catch (Exception e) {
            log.error("agent 调用失败 userId={} sessionId={}", userId, session.getId(), e);
            throw new CommonException("AI 服务调用失败: " + e.getMessage());
        }
        sessionService.appendMessage(session.getId(), "assistant", reply);
        sessionGuard.recordAssistant(session.getId(), reply);
        // P5 会话任务板：本轮 turn 落板（best-effort，失败不影响对话）
        try {
            taskService.recordTurn(session.getId(), userId, agentType,
                    intentDecision.isKnown() ? intentDecision.intent().name() : null, text);
        } catch (Exception e) {
            log.debug("turn 落板失败（忽略）sessionId={}: {}", session.getId(), e.getMessage());
        }
        return new ChatResult(session.getId(), reply, null);
    }

    /**
     * IM 回调回流：把老师 IM 回复投递回 teacher-agent 会话，组织答案（spec §7.3【回】）
     */
    public String deliverImReply(Long sessionId, Long teacherId, String replyText) {
        AgentSession session = sessionService.getOwned(teacherId, sessionId);
        String prompt = "【IM 回流】老师在 IM 小助手中回复了学生的问题，请把回复组织成给学生看的答案（保留原意，补充必要上下文）：\n"
                + replyText;
        try {
            AgentInvocationResult result = agentRuntime.invoke(new AgentInvocation(
                    teacherId,
                    2,
                    session.getId(),
                    session.getAgentType(),
                    null,
                    null,
                    contextAssembler.recallLongTermMemory(new ContextAssemblyRequest(
                            teacherId, 2, session.getId(), session.getAgentType(), prompt)),
                    List.of(AgentInvocation.Message.user(prompt))));
            String answer = result == null || result.reply() == null ? replyText : result.reply();
            sessionService.appendMessage(session.getId(), "user", prompt);
            sessionService.appendMessage(session.getId(), "assistant", answer);
            sessionGuard.recordAssistant(session.getId(), answer);
            return answer;
        } catch (Exception e) {
            log.error("IM 回流处理失败 sessionId={}", sessionId, e);
            return replyText;
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
