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
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 工具工厂（spec §3.3：声明工具列表 → Toolkit；HEAVY_HARNESS_SPEC §6/§7：网关壳化）
 *
 * - 模块工具：按声明 tools 的 id 前缀（course/exam/learning/...）经 {@link ToolDomainRegistry} 取对应 Bean，
 *   并从 Bean 中精确保留 YAML 声明的方法，不按领域整组放行；
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
        rejectDuplicateFunctionNames(decl);
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
                throw new IllegalStateException(
                        "工具域未注册 domain=" + prefix + " agent=" + decl.getName() + "，已拒绝构建 Toolkit");
            }
            toolkit.registerTool(bean);
            Set<String> declaredTools = decl.getTools().stream()
                    .filter(id -> id.startsWith(prefix + "."))
                    .collect(Collectors.toSet());
            registerDeclared(toolkit, prefix, bean, decl.getName(), declaredTools, guarded);
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

    /** AgentScope 以 @Tool.name 暴露函数；不同领域的同名函数不能安全地共存于同一个 Toolkit。 */
    private void rejectDuplicateFunctionNames(AgentDeclaration decl) {
        Map<String, String> ownerByFunctionName = new HashMap<>();
        for (String toolId : decl.getTools()) {
            int separator = toolId.indexOf('.');
            String functionName = separator < 0 ? toolId : toolId.substring(separator + 1);
            String previous = ownerByFunctionName.putIfAbsent(functionName, toolId);
            if (previous != null && !previous.equals(toolId)) {
                throw new IllegalStateException(
                        "工具名冲突 function=" + functionName + " declarations=[" + previous + ", " + toolId
                                + "] agent=" + decl.getName() + "，已拒绝构建 Toolkit");
            }
        }
    }

    /**
     * 声明过滤与网关壳化：移除域 Bean 上未声明的方法，并把声明的 @Tool 方法替换为同名 GuardedFunctionTool。
     * 步骤：getTool(原始) → removeTool → registerAgentTool(壳)。任一失败立即中止 Toolkit 构建，
     * 禁止恢复未经过策略网关的原始工具。
     */
    private void registerDeclared(Toolkit toolkit, String prefix, Object bean, String agentName,
                                  Set<String> declaredTools, boolean guarded) {
        Set<String> found = new HashSet<>();
        for (Method m : bean.getClass().getMethods()) {
            Tool ann = m.getAnnotation(Tool.class);
            if (ann == null) {
                continue;
            }
            String name = ann.name();
            String toolId = prefix + "." + name;
            if (!declaredTools.contains(toolId)) {
                if (toolkit.getTool(name) != null) {
                    toolkit.removeTool(name);
                }
                continue;
            }
            found.add(toolId);
            if (!guarded) {
                continue;
            }
            try {
                AgentTool raw = toolkit.getTool(name);
                if (raw == null) {
                    throw new IllegalStateException("Toolkit 中不存在声明工具 " + toolId);
                }
                toolkit.removeTool(name);
                boolean writeTool = !toolMetaService.resolve(toolId)
                        .map(ToolMetaService.ToolMeta::readOnly).orElse(false);
                toolkit.registerAgentTool(new GuardedFunctionTool(raw, toolId, writeTool,
                        policyEngine, traceService, toolControlService, taskService));
            } catch (Exception e) {
                throw new IllegalStateException(
                        "工具网关壳化失败 tool=" + toolId + " agent=" + agentName + "，已拒绝构建 Toolkit", e);
            }
        }
        Set<String> missing = new HashSet<>(declaredTools);
        missing.removeAll(found);
        if (!missing.isEmpty()) {
            throw new IllegalStateException(
                    "声明工具未注册 tools=" + missing + " agent=" + agentName + "，已拒绝构建 Toolkit");
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
