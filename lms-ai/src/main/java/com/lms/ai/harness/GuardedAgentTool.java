package com.lms.ai.harness;

import com.lms.ai.task.AgentTask;
import com.lms.ai.task.TaskService;
import com.lms.ai.tools.ToolSupport;
import io.agentscope.core.agent.Agent;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.message.ContentBlock;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.message.ToolResultBlock;
import io.agentscope.core.message.ToolUseBlock;
import io.agentscope.core.tool.AgentTool;
import io.agentscope.core.tool.ToolCallParam;
import io.agentscope.core.tool.subagent.SubAgentTool;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

/**
 * 受 PolicyEngine 保护的邀请工具（onBeforeAgentInvite Host 拦截层，spec HEAVY_HARNESS_SPEC §8）
 *
 * 包装 AgentScope 原生 {@link SubAgentTool}：主 Agent 的 ReAct 循环想"邀请"子专家时，
 * 真正执行前先走 {@link PolicyEngine#decideInvite} 确定性决策——
 * ALLOW → 委托原生 SubAgentTool 执行；
 * DENY/ASK → 不执行任何子 agent，直接把决策文本作为工具结果回喂 LLM（让主 Agent 重规划/转述用户）。
 *
 * 深度传播：ALLOW 时把 depth+1 写入 RuntimeContext（子 agent 内部再邀请时读到更深一层），
 * 委托完成后恢复原值，避免污染父级后续调用。
 */
@Slf4j
public class GuardedAgentTool implements AgentTool {

    private final SubAgentTool delegate;
    private final String targetAgent;
    private final PolicyEngine policyEngine;
    private final HarnessTraceService trace;
    private final TaskService taskService;

    public GuardedAgentTool(SubAgentTool delegate, String targetAgent,
                            PolicyEngine policyEngine, HarnessTraceService trace,
                            TaskService taskService) {
        this.delegate = delegate;
        this.targetAgent = targetAgent;
        this.policyEngine = policyEngine;
        this.trace = trace;
        this.taskService = taskService;
    }

    @Override
    public String getName() {
        return delegate.getName();
    }

    @Override
    public String getDescription() {
        return delegate.getDescription();
    }

    @Override
    public Map<String, Object> getParameters() {
        return delegate.getParameters();
    }

    @Override
    public Mono<ToolResultBlock> callAsync(ToolCallParam param) {
        // 组装邀请上下文
        RuntimeContext ctx = param == null ? null : param.getRuntimeContext();
        Agent agent = param == null ? null : param.getAgent();
        String sessionId = ctx == null ? null : ctx.getSessionId();
        Long userId = ctx == null ? null : ctx.get(ToolSupport.CTX_USER_ID, Long.class);
        Integer userType = ctx == null ? null : ctx.get(ToolSupport.CTX_USER_TYPE, Integer.class);
        String inviter = agent == null ? null : agent.getName();
        int depth = ctx == null ? 0 : readDepth(ctx);

        PolicyDecision decision = policyEngine.decideInvite(
                new PolicyEngine.InviteCtx(sessionId, userId, userType, inviter, targetAgent, depth));
        trace.record(sessionId, HarnessTraceService.EVENT_INVITE, Map.of(
                "inviter", orDash(inviter),
                "target", targetAgent,
                "depth", String.valueOf(depth),
                "verdict", decision.verdict().name(),
                "code", orDash(decision.code()),
                "reason", decision.reason() == null ? "" : decision.reason(),
                "hitlRequestId", decision.hitlRequestId() == null ? "" : decision.hitlRequestId()));

        if (decision.isAllowed()) {
            // 任务板：邀请放行落板（P5，仅数字会话；失败不影响主流程）
            try {
                taskService.recordAction(TaskService.numericSession(sessionId), null, userId, inviter,
                        AgentTask.ACTION_INVITE, "invite:" + targetAgent, decision.code());
            } catch (Exception e) {
                log.debug("邀请动作落板失败（忽略）target={}: {}", targetAgent, e.getMessage());
            }
            // 深度 +1 传播给子链，委托完成后恢复
            if (ctx != null) {
                ctx.put(HarnessKeys.CTX_INVITE_DEPTH, depth + 1);
            }
            return delegate.callAsync(param)
                    .doFinally(s -> {
                        if (ctx != null) {
                            ctx.put(HarnessKeys.CTX_INVITE_DEPTH, depth);
                        }
                    });
        }
        // 拒绝 / 转 HITL：不执行任何子 agent，回喂 LLM 让其重规划或转述用户
        return Mono.just(buildDeniedBlock(param, decision));
    }

    /** 构造给 LLM 看的工具结果（文本说明 + 保留工具名便于追踪） */
    private ToolResultBlock buildDeniedBlock(ToolCallParam param, PolicyDecision decision) {
        ToolUseBlock use = param == null ? null : param.getToolUseBlock();
        String reason = decision.reason() == null ? "Harness 拦截" : decision.reason();
        String tip = decision.isAsk()
                ? "\n\n提示：人工确认通过后（前端/接口批准请求），请重新尝试该操作。"
                : "\n\n提示：请基于被允许的能力重新规划，不要重复发起被拦截的邀请。";
        TextBlock text = TextBlock.builder().text(reason + tip).build();
        if (use != null) {
            return new ToolResultBlock(use.getId(), use.getName(), List.<ContentBlock>of(text), Map.of());
        }
        return ToolResultBlock.of(text);
    }

    private int readDepth(RuntimeContext ctx) {
        Integer d = ctx.get(HarnessKeys.CTX_INVITE_DEPTH, Integer.class);
        return d == null ? 0 : d;
    }

    private static String orDash(String s) {
        return s == null ? "-" : s;
    }
}
