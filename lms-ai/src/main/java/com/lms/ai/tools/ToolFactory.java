package com.lms.ai.tools;

import com.lms.ai.config.AiAgentProperties;
import com.lms.ai.config.AgentModelFactory;
import com.lms.ai.config.HarnessV2Properties;
import com.lms.ai.declaration.AgentDeclaration;
import com.lms.ai.harness.GuardedAgentTool;
import com.lms.ai.harness.HarnessTraceService;
import com.lms.ai.harness.PolicyEngine;
import com.lms.ai.harness.ToolMetaService;
import com.lms.ai.registry.AgentRegistry;
import com.lms.ai.task.TaskService;
import io.agentscope.core.ReActAgent;
import io.agentscope.core.tool.AgentTool;
import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.Toolkit;
import io.agentscope.core.tool.subagent.SubAgentConfig;
import io.agentscope.core.tool.subagent.SubAgentTool;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Set;

/**
 * 工具工厂（spec §3.3：声明工具列表 → Toolkit；HEAVY_HARNESS_SPEC §6/§7：网关壳化）
 *
 * - 模块工具：按声明 tools 的 id 前缀（course/exam/learning/...）经 {@link ToolDomainRegistry} 取对应 Bean；
 * - 网关壳化（harness-v2.tool-gateway.enabled=true 时）：
 *   每个 @Tool 方法注册后，用 {@link Toolkit#getTool}/{@link Toolkit#removeTool}/{@link Toolkit#registerAgentTool}
 *   替换为同名 {@link GuardedFunctionTool} —— LLM 看到的工具名/描述/schema 不变（delegate 提供），
 *   但执行前统一过 ToolControlService 运行时开关 + PolicyEngine 决策（角色矩阵/readOnly/risk HITL）；
 * - 子 agent（邀请制，spec §5.1）：SubAgentTool(SubAgentProvider + SubAgentConfig)，父 agent 在 ReAct 循环里
 *   像调工具一样"邀请"子 agent；每次邀请 provide() 新实例，外包 {@link GuardedAgentTool} 走 PolicyEngine。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ToolFactory {

    private final AgentRegistry registry;
    private final AgentModelFactory modelFactory;
    private final AiAgentProperties properties;
    private final HarnessV2Properties v2;
    private final PolicyEngine policyEngine;
    private final HarnessTraceService traceService;
    private final ToolDomainRegistry domainRegistry;
    private final ToolControlService toolControlService;
    private final ToolMetaService toolMetaService;
    private final TaskService taskService;

    /**
     * 按声明构建 Toolkit：注册声明的模块工具（网关壳化）+ 邀请制的子 agent 工具（spec §3.2/§5.1）
     */
    public Toolkit build(AgentDeclaration decl) {
        Toolkit toolkit = new Toolkit();
        Set<String> loaded = new HashSet<>();
        boolean guarded = v2.isEnabled() && v2.isToolGatewayEnabled();
        for (String toolId : decl.getTools()) {
            String prefix = toolId.contains(".") ? toolId.substring(0, toolId.indexOf('.')) : toolId;
            if (!loaded.add(prefix)) {
                continue;
            }
            Object bean = domainRegistry.bean(prefix);
            if (bean == null) {
                log.warn("声明工具前缀无对应 Bean: {}（agent={}）", prefix, decl.getName());
                continue;
            }
            toolkit.registerTool(bean);
            if (guarded) {
                registerGuarded(toolkit, prefix, bean, decl.getName());
            }
        }
        // 邀请制：声明 subAgents → SubAgentTool（静态声明，父调子，推荐主用，spec §5.1 ①）
        for (String subName : decl.getSubAgents()) {
            if (registry.contains(subName)) {
                toolkit.registerAgentTool(subAgentTool(subName));
            } else {
                log.warn("声明子 agent 不存在: {}（agent={}）", subName, decl.getName());
            }
        }
        return toolkit;
    }

    /**
     * 网关壳化：把刚注册的域 Bean 上每个 @Tool 方法替换为同名 GuardedFunctionTool。
     * 步骤：getTool(原始) → removeTool → registerAgentTool(壳)。任一失败则把原始工具放回（warn，不回退整体，
     * 避免 remove 后注册失败导致工具丢失破坏 function-calling）。
     */
    private void registerGuarded(Toolkit toolkit, String prefix, Object bean, String agentName) {
        for (Method m : bean.getClass().getMethods()) {
            Tool ann = m.getAnnotation(Tool.class);
            if (ann == null) {
                continue;
            }
            String name = ann.name();
            String toolId = prefix + "." + name;
            AgentTool raw = null;
            try {
                raw = toolkit.getTool(name);
                if (raw == null) {
                    log.warn("网关壳化跳过：注册表无工具 {}（agent={}）", toolId, agentName);
                    continue;
                }
                toolkit.removeTool(name);
                boolean writeTool = !toolMetaService.resolve(toolId)
                        .map(ToolMetaService.ToolMeta::readOnly).orElse(false);
                toolkit.registerAgentTool(new GuardedFunctionTool(raw, toolId, writeTool,
                        policyEngine, traceService, toolControlService, taskService));
            } catch (Exception e) {
                log.warn("网关壳化失败 tool={} agent={}（回退原始注册）: {}", toolId, agentName, e.getMessage());
                if (raw != null) {
                    try {
                        if (toolkit.getTool(name) == null) {
                            toolkit.registerAgentTool(raw);
                        }
                    } catch (Exception re) {
                        log.error("网关壳化回退失败 tool={}: {}", toolId, re.getMessage());
                    }
                }
            }
        }
    }

    /** 构建子 agent 实例（被邀请者，每次邀请新实例，spec §1.3） */
    public ReActAgent provideSubAgent(String name) {
        AgentDeclaration decl = registry.get(name);
        String sysPrompt = decl.getSystemPrompt();
        // 个人角色声明被子 agent 邀请时，{name} 占位符替换为通用称谓
        sysPrompt = sysPrompt.replace("{name}", "用户");
        Toolkit toolkit = build(decl);
        ReActAgent agent = ReActAgent.builder()
                .name(decl.getName())
                .description(decl.getDescription())
                .sysPrompt(sysPrompt)
                .model(modelFactory.get(decl.getModel() == null ? properties.getSubagentModel() : decl.getModel()))
                .toolkit(toolkit)
                .maxIters(8)
                .build();
        log.debug("构建子 agent 实例: {} (tools={})", name, toolkit.getToolNames());
        return agent;
    }

    /** SubAgentTool：父 agent 的"邀请工具"（agent-as-tool，spec §5.1 ①），外包 PolicyEngine Guard */
    public AgentTool subAgentTool(String name) {
        AgentDeclaration decl = registry.get(name);
        SubAgentConfig config = SubAgentConfig.builder()
                .toolName("invoke_" + name.replace("-", "_"))
                .description(decl.getDescription())
                .build();
        SubAgentTool raw = new SubAgentTool(() -> provideSubAgent(name), config);
        return new GuardedAgentTool(raw, name, policyEngine, traceService, taskService);
    }
}
