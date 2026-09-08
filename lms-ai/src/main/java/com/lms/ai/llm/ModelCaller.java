package com.lms.ai.llm;

import com.lms.ai.config.AgentModelFactory;
import io.agentscope.core.message.ContentBlock;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.message.UserMessage;
import io.agentscope.core.model.ChatResponse;
import io.agentscope.core.model.GenerateOptions;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.List;

/**
 * 模型直调助手（P2 LLM 分类层 / P4 LLM 摘要压缩 共用的轻量通道）
 *
 * 用法：单轮 system+user 文本 → 模型输出全文（默认关闭流式聚合结果差异，取聚合文本）。
 * 失败/超时返回 null（调用方走确定性兜底，绝不阻塞主流程）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ModelCaller {

    private static final Duration TIMEOUT = Duration.ofSeconds(20);

    private final AgentModelFactory modelFactory;

    /**
     * 单轮对话取文本
     *
     * @param modelName 模型名（qwen-turbo / qwen-plus / qwen-max）
     * @param system    system 提示（可空）
     * @param user      用户输入
     * @param maxTokens 输出上限（分类 64~256，摘要可大）
     */
    public String chat(String modelName, String system, String user, int maxTokens) {
        if (user == null || user.isBlank()) {
            return null;
        }
        String text = (system == null || system.isBlank() ? "" : system + "\n\n") + user;
        try {
            GenerateOptions options = GenerateOptions.builder()
                    .temperature(0.0)
                    .maxTokens(maxTokens)
                    .build();
            Flux<ChatResponse> flux = modelFactory.get(modelName)
                    .stream(List.<Msg>of(new UserMessage(text)), List.of(), options);
            StringBuilder out = new StringBuilder();
            flux.doOnNext(resp -> {
                if (resp.getContent() != null) {
                    for (ContentBlock b : resp.getContent()) {
                        if (b instanceof TextBlock tb && tb.getText() != null) {
                            out.append(tb.getText());
                        }
                    }
                }
            }).blockLast(TIMEOUT);
            String result = out.toString().trim();
            return result.isEmpty() ? null : result;
        } catch (Exception e) {
            log.warn("模型直调失败 model={}: {}", modelName, e.getMessage());
            return null;
        }
    }
}
