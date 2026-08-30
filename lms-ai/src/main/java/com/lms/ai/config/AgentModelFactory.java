package com.lms.ai.config;

import io.agentscope.core.model.Model;
import io.agentscope.extensions.model.openai.OpenAIChatModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Agent 模型工厂（spec §10：模型分级，控制成本）
 *
 * 按模型名构建/缓存 OpenAIChatModel（DashScope OpenAI 兼容端点，api-key 走环境变量）。
 * 默认单模型 bean 由 agentscope-openai-spring-boot-starter 注入（ChatService 用），
 * 本工厂为 agent 声明按需构建 qwen-max/qwen-turbo 等额外实例。
 */
@Component
public class AgentModelFactory {

    @Value("${agentscope.openai.api-key:}")
    private String apiKey;

    @Value("${agentscope.openai.base-url:https://dashscope.aliyuncs.com/compatible-mode/v1}")
    private String baseUrl;

    private final Map<String, Model> cache = new ConcurrentHashMap<>();

    public Model get(String modelName) {
        return cache.computeIfAbsent(modelName, this::build);
    }

    private Model build(String modelName) {
        return OpenAIChatModel.builder()
                .baseUrl(baseUrl)
                .apiKey(apiKey == null ? "" : apiKey)
                .modelName(modelName)
                .stream(true)
                .build();
    }
}
