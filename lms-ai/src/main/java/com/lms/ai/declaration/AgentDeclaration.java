package com.lms.ai.declaration;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * Agent 声明（spec §3.1：声明式模型，来源 resources/agents/*.yaml）
 *
 * 对齐 AgentScope Java：Agent 由 name/description/system prompt/模型/工具组成；
 * 子 agent 用 SubAgentTool 暴露给父 agent（邀请制互调，spec §1.3/§5.1）。
 */
@Data
public class AgentDeclaration {

    /** Agent 名（全局唯一，如 exam-agent / teacher-agent） */
    private String name;

    /** 描述：供 LLM 判断何时邀请/使用 */
    private String description;

    /** 角色：personal（个人 agent，常驻编排者）/ sub（子 agent，被邀请者）/ tool（纯工具集合） */
    private String role = "sub";

    /** 模型（可选，缺省走配置 lms.ai.agent.subagent-model） */
    private String model;

    /** 系统提示词（可含 {name}/{role} 占位符，按用户实例化时注入，spec §3.2） */
    private String systemPrompt;

    /** 工具列表（ToolFactory 注册，id 形如 exam.saveQuestion，spec §3.3） */
    private List<String> tools = new ArrayList<>();

    /** 邀请的子 agent 列表（个人 agent 声明 subAgents，spec §3.1） */
    private List<String> subAgents = new ArrayList<>();

    /** 绑定知识库（kb 名列表：teacher-kb / course-kb 等） */
    private List<String> knowledge = new ArrayList<>();

    /** 记忆层配置（layer1 会话 / layer2 画像 / layer3 知识库） */
    private MemoryDeclaration memory = new MemoryDeclaration();

    /** 角色枚举常量 */
    public static final String ROLE_PERSONAL = "personal";
    public static final String ROLE_SUB = "sub";
    public static final String ROLE_TOOL = "tool";

    public boolean isPersonal() {
        return ROLE_PERSONAL.equals(role);
    }
}
