package com.lms.kb.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * AI 服务配置（前缀 lms.ai，api-key 一律走环境变量，不写死在配置里）
 *
 * 对应 Python 版 llm.py：
 * - deepseek-reasoner（R1）：rewrite 查询改写
 * - deepseek-chat：HyDE 假设性回答
 * - qwen-vl-max（DashScope OpenAI 兼容端点）：生成（多模态）
 * - text-embedding-v3（DashScope 原生端点）：向量化
 * - gte-rerank-v2（DashScope 原生端点）：精排
 */
@Data
@ConfigurationProperties(prefix = "lms.ai")
public class AiProperties {

    private String dashscopeApiKey = "";
    private String dashscopeBaseUrl = "https://dashscope.aliyuncs.com/compatible-mode/v1";
    private String dashscopeNativeUrl = "https://dashscope.aliyuncs.com/api/v1";
    private String qwenVlModel = "qwen-vl-max";
    private String embeddingModel = "text-embedding-v3";
    private String rerankModel = "gte-rerank-v2";

    private String deepseekApiKey = "";
    private String deepseekBaseUrl = "https://api.deepseek.com";
    private String deepseekReasonerModel = "deepseek-reasoner";
    private String deepseekChatModel = "deepseek-chat";
}
