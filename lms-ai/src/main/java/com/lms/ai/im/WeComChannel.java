package com.lms.ai.im;

import cn.hutool.crypto.digest.DigestUtil;
import cn.hutool.http.HttpUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lms.ai.config.ImProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * 企微群机器人渠道（spec §7.2/§7.3：webhook 推送 + 回调验签）
 *
 * - 推送：POST 企微群机器人 webhook，text 消息体
 * - 回调：企微回调带 msg_signature/timestamp/nonce/echostr 等，验签后解析为 ImMessage
 *   （练手实现简化验签：HMAC-SHA256(callbackSecret, timestamp + nonce + body)；真实平台按官方签名算法扩展）
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WeComChannel implements ImChannel {

    private final ImProperties properties;
    private final ObjectMapper objectMapper;

    @Override
    public ImSendResult push(ImMessage msg) {
        if (properties.getWecomWebhook() == null || properties.getWecomWebhook().isBlank()) {
            return ImSendResult.fail("企微 webhook 未配置（lms.ai.im.wecom-webhook）");
        }
        try {
            Map<String, Object> body = new HashMap<>();
            body.put("msgtype", "text");
            Map<String, Object> text = new HashMap<>();
            text.put("content", msg.text());
            body.put("text", text);
            String resp = HttpUtil.post(properties.getWecomWebhook(), objectMapper.writeValueAsString(body));
            log.info("[IM-wecom] push to={} resp={}", msg.to(), resp);
            // 企微返回 {"errcode":0,"errmsg":"ok"} 视为成功
            if (resp != null && resp.contains("\"errcode\":0")) {
                return ImSendResult.ok("企微推送成功");
            }
            return ImSendResult.fail("企微推送失败: " + resp);
        } catch (Exception e) {
            log.error("企微推送异常", e);
            return ImSendResult.fail("企微推送异常: " + e.getMessage());
        }
    }

    @Override
    public ImMessage parseCallback(Map<String, Object> payload) {
        // 验签：HMAC-SHA256(secret, timestamp+nonce+body)；失败抛异常（spec §7.3 安全：回调必须验签）
        String timestamp = payload.get("timestamp") == null ? "" : String.valueOf(payload.get("timestamp"));
        String nonce = payload.get("nonce") == null ? "" : String.valueOf(payload.get("nonce"));
        String signature = payload.get("msg_signature") == null ? "" : String.valueOf(payload.get("msg_signature"));
        String body = payload.get("body") == null ? "" : String.valueOf(payload.get("body"));
        String expected = DigestUtil.sha256Hex(properties.getCallbackSecret() + timestamp + nonce + body);
        if (!expected.equalsIgnoreCase(signature)) {
            throw new IllegalArgumentException("企微回调验签失败");
        }
        // 简化解析：body 为文本消息 JSON（{"content":"...","taskId":"...","sessionId":"..."}）
        String text = payload.get("content") == null ? "" : String.valueOf(payload.get("content"));
        String taskId = payload.get("taskId") == null ? null : String.valueOf(payload.get("taskId"));
        String sessionId = payload.get("sessionId") == null ? null : String.valueOf(payload.get("sessionId"));
        Long userId = payload.get("userId") == null ? null : Long.valueOf(payload.get("userId").toString());
        return new ImMessage("teacher-assistant", text, taskId, sessionId, userId);
    }

    @Override
    public String platform() {
        return "wecom";
    }
}
