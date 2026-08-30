package com.lms.ai.tools;

import com.lms.ai.config.AiAgentProperties;
import com.lms.ai.config.AgentModelFactory;
import com.lms.ai.declaration.AgentDeclaration;
import com.lms.ai.registry.AgentRegistry;
import io.agentscope.core.ReActAgent;
import io.agentscope.core.ReActAgent;
import io.agentscope.core.tool.Toolkit;
import io.agentscope.core.tool.subagent.SubAgentConfig;
import io.agentscope.core.tool.subagent.SubAgentTool;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Set;

/**
 * 工具工厂（spec §3.3：ToolFactory —— 声明工具列表 → Toolkit）
 *
 * - 模块工具：按声明 tools 的 id 前缀（course/exam/learning/...）注册对应工具 Bean
 *   （Feign Client 方法 → @Tool 方法，自动生成参数 JSON Schema 与描述）
 * - 子 agent（邀请制，spec §5.1）：SubAgentTool(SubAgentProvider + SubAgentConfig)，
 *   父 agent 在 ReAct 循环里像调工具一样"邀请"子 agent；每次邀请 provide() 新实例
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ToolFactory {

    private final AgentRegistry registry;
    private final AgentModelFactory modelFactory;
    private final AiAgentProperties properties;

    // 声明工具前缀 → 工具 Bean（一个子 agent 一组 ToolGroup 的简化：整 Bean 注册）
    private final CourseTools courseTools;
    private final ExamTools examTools;
    private final LearningTools learningTools;
    private final SearchTools searchTools;
    private final RemarkTools remarkTools;
    private final UserTools userTools;
    private final MediaTools mediaTools;
    private final StatisticsTools statisticsTools;
    private final KbTools kbTools;
    private final ImTools imTools;
    private final TaskTools taskTools;
    private final EvalTools evalTools;
    private final LearningPipelineTools learningPipelineTools;

    /**
     * 按声明构建 Toolkit：注册声明的模块工具 + 邀请制的子 agent 工具（spec §3.2/§5.1）
     */
    public Toolkit build(AgentDeclaration decl) {
        Toolkit toolkit = new Toolkit();
        Set<String> loaded = new HashSet<>();
        for (String toolId : decl.getTools()) {
            String prefix = toolId.contains(".") ? toolId.substring(0, toolId.indexOf('.')) : toolId;
            if (!loaded.add(prefix)) {
                continue;
            }
            Object bean = toolBean(prefix);
            if (bean != null) {
                toolkit.registerTool(bean);
            } else {
                log.warn("声明工具前缀无对应 Bean: {}（agent={}）", prefix, decl.getName());
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

    /** 声明工具前缀 → 工具 Bean（惰性映射，避免构造器赋值顺序问题） */
    private Object toolBean(String prefix) {
        return switch (prefix) {
            case "course" -> courseTools;
            case "exam" -> examTools;
            case "learning" -> learningTools;
            case "search" -> searchTools;
            case "remark" -> remarkTools;
            case "user" -> userTools;
            case "media" -> mediaTools;
            case "statistics" -> statisticsTools;
            case "kb" -> kbTools;
            case "im" -> imTools;
            case "task" -> taskTools;
            case "eval" -> evalTools;
            case "pipeline" -> learningPipelineTools;
            default -> null;
        };
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

    /** SubAgentTool：父 agent 的"邀请工具"（agent-as-tool，spec §5.1 ①） */
    public SubAgentTool subAgentTool(String name) {
        AgentDeclaration decl = registry.get(name);
        SubAgentConfig config = SubAgentConfig.builder()
                .toolName("invoke_" + name.replace("-", "_"))
                .description(decl.getDescription())
                .build();
        return new SubAgentTool(() -> provideSubAgent(name), config);
    }
}
