package com.lms.ai.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * AgentScope ReMe 记忆配置（前缀 lms.ai.agent.reme，spec §4 长期记忆对接）
 *
 * ReMe 是阿里通义实验室的检索增强分层记忆系统（AgentScope 官方记忆扩展
 * agentscope-extensions-reme）：对话轨迹 → 记忆提取-遗忘-整合 → 检索注入，
 * 对应 spec §4.3 的画像/习惯记忆（L2）。需先部署 ReMe 服务端
 * （官方仓库 agentscope-ai/ReMe，Docker 本地部署或通义百炼云服务），
 * 再把 base-url 填到此处；未配置时自动回退 MySQL 画像记忆（ProfileLongTermMemory）。
 */
@Data
@ConfigurationProperties(prefix = "lms.ai.agent.reme")
public class ReMeProperties {

    /** 是否启用 ReMe 长期记忆（需已部署 ReMe 服务端；未启用回退 ProfileLongTermMemory） */
    private boolean enabled = false;

    /** ReMe 服务端地址（如 http://localhost:8000；云服务填平台提供的 endpoint） */
    private String baseUrl = "";

    /** 请求超时（毫秒，默认 3s——避免记忆检索拖慢对话，官方默认 60s 偏长） */
    private long timeoutMs = 3000;

    /** 记忆空间前缀：workspaceId = prefix + userId（每人一个记忆空间，对应"每人一个 agent"） */
    private String workspacePrefix = "user_";
}
