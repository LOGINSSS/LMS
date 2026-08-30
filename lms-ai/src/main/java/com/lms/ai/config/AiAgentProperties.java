package com.lms.ai.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 个人 Agent 运行时配置（前缀 lms.ai.agent，spec §9 阶段开关）
 *
 * 对应 application.yml / Nacos lms-ai.yaml 的 lms.ai.agent.*。
 */
@Data
@ConfigurationProperties(prefix = "lms.ai.agent")
public class AiAgentProperties {

    /** Agent 运行时总开关（阶段 1；关闭则 /agent/** 返回不可用） */
    private boolean enabled = true;

    /** 子 Agent 声明文件路径（classpath 下目录，默认 agents，spec §3.2） */
    private String declarationPath = "agents";

    /** 模型分级（spec §10：控制成本）：编排（个人 agent）用 */
    private String orchestratorModel = "qwen-max";

    /** 子 agent 默认模型 */
    private String subagentModel = "qwen-plus";

    /** 画像摘要压缩模型 */
    private String summaryModel = "qwen-turbo";

    /** L1 会话消息 Redis TTL（天，spec §4.1：1~7 天） */
    private int sessionTtlDays = 7;

    /** 互调深度限制（spec §5.3：≤3） */
    private int maxInvokeDepth = 3;

    /** 高副作用工具前置人工确认（阶段 5 加固项，当前提示不拦截） */
    private boolean requireConfirm = false;
}
