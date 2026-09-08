package com.lms.ai.tools;

import com.lms.ai.harness.HarnessKeys;
import com.lms.ai.harness.HarnessTraceService;
import com.lms.ai.harness.PolicyDecision;
import com.lms.ai.harness.PolicyEngine;
import com.lms.ai.task.AgentTask;
import com.lms.ai.task.TaskService;
import io.agentscope.core.agent.Agent;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.message.ContentBlock;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.message.ToolResultBlock;
import io.agentscope.core.message.ToolUseBlock;
import io.agentscope.core.tool.AgentTool;
import io.agentscope.core.tool.ToolCallParam;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 受 PolicyEngine 保护的工具执行网关（spec HEAVY_HARNESS_SPEC §8：CALL_TOOL 动作级管控）
 *
 * 包装 AgentScope 原生注册的业务工具（{@code Toolkit.getTool(name)} 取到的 ReflectiveFunctionTool），
 * 以同名重注册方式替换：LLM 视角工具名/描述/参数 schema 完全不变（delegate.getParameters()），
 * 但真正执行前先过：
 *   1. ToolControlService 运行时开关（静态名单 + Redis 动态关停）；
 *   2. PolicyEngine.decideTool —— 角色矩阵（如学生直调 exam.saveQuestion → DENY）、
 *      readOnly/risk 分级（risk=high → 动作级 HITL ask/deny/log）、pipelineToken 豁免。
 * DENY/ASK → 不执行任何业务代码，构造文本结果回喂 LLM 重规划/转述用户。
 */
@Slf4j
public class GuardedFunctionTool implements AgentTool {

    private final AgentTool delegate;
    private final String toolId;
    private final boolean writeTool;
    private final PolicyEngine policyEngine;
    private final HarnessTraceService trace;
    private final ToolControlService toolControl;
    private final TaskService taskService;

    public GuardedFunctionTool(AgentTool delegate, String toolId, boolean writeTool,
                               PolicyEngine policyEngine, HarnessTraceService trace,
                               ToolControlService toolControl, TaskService taskService) {
        this.delegate = delegate;
        this.toolId = toolId;
        this.writeTool = writeTool;
        this.policyEngine = policyEngine;
        this.trace = trace;
        this.toolControl = toolControl;
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
        RuntimeContext ctx = param == null ? null : param.getRuntimeContext();
        Agent agent = param == null ? null : param.getAgent();
        String sessionId = ctx == null ? null : ctx.getSessionId();
        Long userId = ctx == null ? null : ctx.get(ToolSupport.CTX_USER_ID, Long.class);
        Integer userType = ctx == null ? null : ctx.get(ToolSupport.CTX_USER_TYPE, Integer.class);
        String agentName = agent == null ? null : agent.getName();
        String pipelineToken = ctx == null ? null : ctx.get(HarnessKeys.CTX_PIPELINE_TOKEN, String.class);

        // 1. 运行时开关
        Optional<String> disabled = toolControl.disabledReason(toolId);
        if (disabled.isPresent()) {
            trace.record(sessionId, HarnessTraceService.EVENT_TOOL, Map.of(
                    "tool", toolId, "agent", orDash(agentName), "verdict", "DENY",
                    "code", "SWITCH_OFF", "reason", disabled.get()));
            return Mono.just(denied(param, disabled.get()));
        }

        // 2. PolicyEngine 决策
        PolicyDecision decision = policyEngine.decideTool(new PolicyEngine.ToolCtx(
                sessionId, userId, userType, agentName, toolId,
                param == null || param.getInput() == null ? Map.of() : param.getInput(),
                pipelineToken));
        trace.record(sessionId, HarnessTraceService.EVENT_TOOL, Map.of(
                "tool", toolId,
                "agent", orDash(agentName),
                "verdict", decision.verdict().name(),
                "code", orDash(decision.code()),
                "reason", decision.reason() == null ? "" : decision.reason(),
                "hitlRequestId", decision.hitlRequestId() == null ? "" : decision.hitlRequestId()));

        if (decision.isAllowed()) {
            // 任务板：写工具放行落板（P5：tool 级只落写工具，读工具走 Trace；失败不影响主流程）
            if (writeTool) {
                try {
                    taskService.recordAction(TaskService.numericSession(sessionId), null, userId, agentName,
                            AgentTask.ACTION_TOOL, toolId, decision.code());
                } catch (Exception e) {
                    log.debug("工具动作落板失败（忽略）tool={}: {}", toolId, e.getMessage());
                }
            }
            return delegate.callAsync(param);
        }
        return Mono.just(denied(param, decision.reason() == null ? "Harness 拦截" : decision.reason()));
    }

    private ToolResultBlock denied(ToolCallParam param, String reason) {
        ToolUseBlock use = param == null ? null : param.getToolUseBlock();
        TextBlock text = TextBlock.builder()
                .text(reason + "\n\n提示：请基于被允许的能力重新规划，不要重复发起被拦截的调用。")
                .build();
        if (use != null) {
            return new ToolResultBlock(use.getId(), use.getName(), List.<ContentBlock>of(text), Map.of());
        }
        return ToolResultBlock.of(text);
    }

    private static String orDash(String s) {
        return s == null ? "-" : s;
    }
}
