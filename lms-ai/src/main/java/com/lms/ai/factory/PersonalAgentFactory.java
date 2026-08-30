package com.lms.ai.factory;

import com.lms.ai.config.AiAgentProperties;
import com.lms.ai.config.AgentModelFactory;
import com.lms.ai.declaration.AgentDeclaration;
import com.lms.ai.memory.AgentUserProfile;
import com.lms.ai.memory.ProfileLongTermMemory;
import com.lms.ai.memory.ProfileService;
import com.lms.ai.memory.ReMeMemoryFactory;
import com.lms.ai.registry.AgentRegistry;
import com.lms.ai.tools.ToolFactory;
import io.agentscope.core.ReActAgent;
import io.agentscope.core.memory.LongTermMemory;
import io.agentscope.core.memory.LongTermMemoryMode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 个人 Agent 工厂（spec §2.1/§3.2：按 user_id + 角色创建/复用个人 agent 实例）
 *
 * - 个人 agent（teacher/student）是"常驻编排者"，绑定角色白名单可邀请的子 agent（subAgents 声明）
 * - prompt 模板注入用户名（{name} 占位符），绑定该用户画像（L2）与知识库（L3）
 * - L2 画像摘要回答前注入 system prompt（spec §4.3：StaticLongTermMemoryHook 语义的构建期实现）
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PersonalAgentFactory {

    private final AgentRegistry registry;
    private final ToolFactory toolFactory;
    private final AgentModelFactory modelFactory;
    private final AiAgentProperties properties;
    private final ProfileService profileService;
    private final ProfileLongTermMemory profileMemory;
    private final ReMeMemoryFactory remeMemoryFactory;

    /**
     * 构建个人 agent（每次对话新建实例，L1 消息由 AgentChatService 管理，无跨会话状态污染）
     *
     * @param userId    用户 id
     * @param userType  1 学生 / 2 老师
     * @param agentType student-agent / teacher-agent
     */
    public ReActAgent build(Long userId, Integer userType, String agentType) {
        AgentDeclaration decl = registry.get(agentType);
        AgentUserProfile profile = profileService.getOrCreate(userId, userType, null);
        String roleName = userType != null && userType == 2 ? "老师" : "学生";

        // 1. system prompt：模板注入用户名 + 画像注入（L2，spec §4.3）
        StringBuilder sysPrompt = new StringBuilder(decl.getSystemPrompt()
                .replace("{name}", profile.getDisplayName() == null ? roleName : profile.getDisplayName()));
        String profileInjection = profileMemory.retrieveFor(userId);
        if (!profileInjection.isBlank()) {
            sysPrompt.append("\n\n【用户画像（长期记忆 L2，回答时参考）】\n").append(profileInjection);
        }
        sysPrompt.append("\n\n【会话规则】\n")
                .append("- 当前用户是").append(roleName).append("（userId=").append(userId).append("）\n")
                .append("- 涉及他人数据/越权操作直接拒绝；落库类操作必须调用工具\n")
                .append("- 需要人工/长任务时使用 im.pushMessage 与 task.schedule 工具\n");

        // 2. Toolkit：声明工具 + 邀请子 agent（SubAgentTool）
        var toolkit = toolFactory.build(decl);

        // 3. ReActAgent（编排模型 qwen-max，spec §10 模型分级）
        // L2 长期记忆：优先 AgentScope ReMe（阿里通义记忆方案：对话轨迹提取-整合-检索注入），
        // 未部署 ReMe 服务端时回退 MySQL 画像记忆；STATIC_CONTROL：静态注入，禁止 agent 自主写污染（spec §4.1/§10）
        LongTermMemory longTermMemory = remeMemoryFactory.get(userId);
        ReActAgent agent = ReActAgent.builder()
                .name(decl.getName())
                .description(decl.getDescription())
                .sysPrompt(sysPrompt.toString())
                .model(modelFactory.get(properties.getOrchestratorModel()))
                .toolkit(toolkit)
                .maxIters(12)
                .longTermMemory(longTermMemory)
                .longTermMemoryMode(LongTermMemoryMode.STATIC_CONTROL)
                .build();
        log.debug("构建个人 agent: agentType={} userId={} tools={} ltm={}",
                agentType, userId, toolkit.getToolNames(), longTermMemory.getClass().getSimpleName());
        return agent;
    }
}
