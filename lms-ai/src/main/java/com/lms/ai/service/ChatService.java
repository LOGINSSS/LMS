package com.lms.ai.service;

import io.agentscope.core.message.Msg;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.message.UserMessage;
import io.agentscope.core.model.ChatResponse;
import io.agentscope.core.model.GenerateOptions;
import io.agentscope.core.model.Model;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.stream.Collectors;

/**
 * LLM 对话服务：最小集成示例（AgentScope Java v2 + OpenAI 兼容接口 → DashScope）
 * <p>模型 Bean 由 agentscope-openai-spring-boot-starter 自动配置注入（配置见 application.yml agentscope.openai.*）
 */
@Service
@RequiredArgsConstructor
public class ChatService {

    /** OpenAIChatModel（DashScope OpenAI 兼容模式） */
    private final Model model;

    /**
     * 单轮对话，返回完整文本
     */
    public String chat(String prompt) {
        List<Msg> msgs = List.of(new UserMessage(prompt));
        // 流式调用：聚合所有增量文本块
        Flux<ChatResponse> flux = model.stream(msgs, List.of(), GenerateOptions.builder().build());
        return flux
                .flatMapIterable(ChatResponse::getContent)
                .filter(TextBlock.class::isInstance)
                .map(block -> ((TextBlock) block).getText())
                .collect(Collectors.joining())
                .block();
    }
}
