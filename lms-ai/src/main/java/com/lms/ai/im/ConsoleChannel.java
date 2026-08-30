package com.lms.ai.im;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Console 渠道（spec §7.2：练手兜底，无真实平台也能跑通全链路）
 *
 * 只打日志 + 记录，验证 agent → IM 推送、IM → agent 回流的编排链路。
 */
@Slf4j
@Component
public class ConsoleChannel implements ImChannel {

    @Override
    public ImSendResult push(ImMessage msg) {
        log.info("[IM-console] push to={} taskId={} sessionId={} userId={}: {}",
                msg.to(), msg.taskId(), msg.sessionId(), msg.userId(), msg.text());
        return ImSendResult.ok("console 已记录（模拟推送）: " + msg.text());
    }

    @Override
    public ImMessage parseCallback(Map<String, Object> payload) {
        // console 渠道无真实回调；约定 payload 字段 text/taskId/sessionId/userId（测试用）
        String text = String.valueOf(payload.getOrDefault("text", ""));
        String taskId = payload.get("taskId") == null ? null : String.valueOf(payload.get("taskId"));
        String sessionId = payload.get("sessionId") == null ? null : String.valueOf(payload.get("sessionId"));
        Long userId = payload.get("userId") == null ? null : Long.valueOf(payload.get("userId").toString());
        return new ImMessage("teacher-assistant", text, taskId, sessionId, userId);
    }

    @Override
    public String platform() {
        return "console";
    }
}
