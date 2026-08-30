package com.lms.ai.controller;

import com.lms.ai.im.ImService;
import com.lms.common.domain.R;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * IM 平台回调接口（spec §7.3【回】/§8.2）
 *
 * 网关白名单放行 /agent/im/callback/**，但本接口必须验签（渠道 parseCallback 校验
 * 平台签名 + 时间戳防重放），且回调只做"投递"不做高副作用动作（spec §10 安全）。
 */
@Tag(name = "IM 回调")
@RestController
@RequestMapping("/agent/im/callback")
@RequiredArgsConstructor
public class ImCallbackController {

    private final ImService imService;

    @PostMapping("/{platform}")
    @Operation(summary = "平台消息回调（验签），回流到对应 agent 会话")
    public R<String> callback(@PathVariable("platform") String platform,
                              @RequestBody Map<String, Object> payload) {
        // 时间戳防重放：回调须携带 timestamp，5 分钟内有效
        Object ts = payload.get("timestamp");
        if (ts == null) {
            return R.fail("缺少时间戳");
        }
        try {
            long timestamp = Long.parseLong(String.valueOf(ts));
            if (Math.abs(System.currentTimeMillis() / 1000 - timestamp) > 300) {
                return R.fail("回调已过期（防重放）");
            }
        } catch (NumberFormatException e) {
            return R.fail("时间戳格式错误");
        }
        String answer = imService.routeCallback(payload);
        return R.ok(answer);
    }
}
