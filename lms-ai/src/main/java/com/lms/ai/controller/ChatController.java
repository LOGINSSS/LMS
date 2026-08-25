package com.lms.ai.controller;

import com.lms.ai.service.ChatService;
import com.lms.common.domain.R;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * AI 对话接口（最小集成示例，后续按业务场景扩展 Agent）
 */
@Tag(name = "AI 对话")
@RestController
@RequestMapping("/ai")
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;

    @PostMapping("/chat")
    @Operation(summary = "LLM 单轮对话（跑通 AgentScope 集成）")
    public R<String> chat(@RequestBody @Valid ChatRequest request) {
        return R.ok(chatService.chat(request.prompt()));
    }

    /**
     * 简单入参（环境准备阶段用 record，后续按业务建 FormDTO）
     */
    public record ChatRequest(@NotBlank(message = "请输入问题") String prompt) {
    }
}
