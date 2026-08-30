package com.lms.ai.controller;

import com.lms.ai.memory.ProfileService;
import com.lms.common.domain.R;
import com.lms.common.utils.UserContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 行为事件上报接口（spec §4.3：L2 画像原料）
 *
 * 事件来源：业务模块 Feign 回调（推荐，可信度高）或前端埋点经网关转发；
 * 事件类型：chat/ask_question/answer/quiz/sign_in/search/like/kb_upload...
 */
@Tag(name = "行为事件")
@RestController
@RequestMapping("/agent/behaviors")
@RequiredArgsConstructor
public class BehaviorController {

    private final ProfileService profileService;

    @PostMapping
    @Operation(summary = "上报行为事件（写入 L2 画像流水，每 N 条触发摘要压缩）")
    public R<Void> report(@RequestBody @Valid BehaviorRequest req) {
        Long userId = UserContext.getUser();
        profileService.recordBehavior(userId, req.eventType(), req.payload());
        return R.ok();
    }

    public record BehaviorRequest(@NotBlank(message = "事件类型不能为空") String eventType,
                                  Map<String, Object> payload) {
    }
}
