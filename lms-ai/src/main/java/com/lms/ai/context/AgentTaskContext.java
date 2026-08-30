package com.lms.ai.context;

import lombok.Builder;
import lombok.Data;

import java.util.Set;

/**
 * Agent 任务上下文（spec §5.3：互调带任务上下文，防止子 agent 越权）
 *
 * 贯穿「个人 agent → 子 agent → 工具」整条互调链：
 * - taskId：幂等键（agent_task.task_id）
 * - ownerId / ownerType：发起用户
 * - parentAgent / depth：编排链路（互调深度 ≤ maxInvokeDepth）
 * - allowedTools：工具白名单（空 = 不限制，按角色白名单已在声明层约束）
 */
@Data
@Builder
public class AgentTaskContext {

    /** 业务幂等键（可 UUID） */
    private String taskId;

    /** 发起用户 id */
    private Long ownerId;

    /** 发起用户类型（1 学生 / 2 老师） */
    private Integer ownerType;

    /** 当前编排深度（个人 agent=1，子 agent=2，工具=3，spec §5.3 ≤3） */
    private int depth;

    /** 父 agent 名 */
    private String parentAgent;

    /** 工具白名单（空集合 = 不额外限制） */
    private Set<String> allowedTools;

    /** RuntimeContext 中的键名 */
    public static final String KEY = "agentTaskContext";
}
