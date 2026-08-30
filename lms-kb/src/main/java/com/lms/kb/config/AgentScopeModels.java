package com.lms.kb.config;

import io.agentscope.core.model.Model;
import io.agentscope.extensions.model.openai.OpenAIChatModel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * AgentScope 模型实例配置（对应 Python 版 llm.py 的四个模型客户端）
 *
 * 全部用 OpenAIChatModel.builder() 手动构建（不走 spring-boot-starter 自动装配）：
 * - rewriteModel  ：DeepSeek deepseek-reasoner（R1），查询改写
 * - hydeModel     ：DeepSeek deepseek-chat，HyDE 假设性回答
 * - generatorModel：DashScope qwen-vl-max（OpenAI 兼容端点），最终生成（多模态模型，文本输入）
 */
@Configuration
public class AgentScopeModels {

    @Bean("rewriteModel")
    public Model rewriteModel(AiProperties props) {
        return openAI(props.getDeepseekBaseUrl(), props.getDeepseekApiKey(), props.getDeepseekReasonerModel());
    }

    @Bean("hydeModel")
    public Model hydeModel(AiProperties props) {
        return openAI(props.getDeepseekBaseUrl(), props.getDeepseekApiKey(), props.getDeepseekChatModel());
    }

    @Bean("generatorModel")
    public Model generatorModel(AiProperties props) {
        return openAI(props.getDashscopeBaseUrl(), props.getDashscopeApiKey(), props.getQwenVlModel());
    }

    private Model openAI(String baseUrl, String apiKey, String modelName) {
        return OpenAIChatModel.builder()
                .baseUrl(baseUrl)
                .apiKey(apiKey)
                .modelName(modelName)
                .stream(true)
                .build();
    }
}
