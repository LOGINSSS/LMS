package com.lms.ai.im;

import com.lms.ai.chat.AgentChatService;
import com.lms.ai.config.ImProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * IM 管道服务（spec §7.2/§7.3：渠道可插拔 + 双向流程）
 *
 * 推：agent → 老师小助手（按配置渠道推送）
 * 回：平台回调 → 验签解析 → 投递回 teacher-agent 会话 → 答案回推
 * 练手默认 ConsoleChannel（无真实平台也能跑通全链路），wecom 配置 webhook 后启用真实推送。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ImService {

    private final ImProperties properties;
    private final List<ImChannel> channels;

    /** @Lazy 打破循环依赖：ImService → AgentChatService → PersonalAgentFactory → ToolFactory → ImTools → ImService */
    @Autowired
    @Lazy
    private AgentChatService chatService;

    /** 当前启用的渠道（按配置选择，未知渠道回落 console） */
    public ImChannel channel() {
        return channels.stream()
                .filter(c -> c.platform().equalsIgnoreCase(properties.getChannel()))
                .findFirst()
                .orElseGet(() -> channels.stream()
                        .filter(c -> "console".equals(c.platform()))
                        .findFirst()
                        .orElseThrow(() -> new IllegalStateException("无可用 IM 渠道")));
    }

    /** 推送一条消息到目标（老师小助手） */
    public ImSendResult push(ImMessage msg) {
        ImSendResult r = channel().push(msg);
        log.info("IM 推送 result={} to={}", r.success(), msg.to());
        return r;
    }

    /**
     * 平台回调处理（IM → agent 回流，spec §7.3【回】）：
     * 验签解析 → 按 sessionId 定位老师会话 → 投递回 teacher-agent → 组织答案 → 回推学生
     */
    public String routeCallback(Map<String, Object> payload) {
        ImMessage msg = channel().parseCallback(payload);
        if (msg.sessionId() == null || msg.userId() == null) {
            throw new IllegalArgumentException("回调缺少 sessionId/userId，无法定位会话");
        }
        log.info("IM 回流: sessionId={} userId={} taskId={} text={}",
                msg.sessionId(), msg.userId(), msg.taskId(), msg.text());
        // 投递回 teacher-agent 会话（老师回复 → agent 组织答案）
        String teacherReply = chatService.deliverImReply(Long.valueOf(msg.sessionId()), msg.userId(), msg.text());
        // 答案回推学生（console 记录 / 真实渠道推送）
        ImMessage reply = new ImMessage("student-" + msg.userId(), teacherReply, msg.taskId(), msg.sessionId(), msg.userId());
        push(reply);
        return teacherReply;
    }
}
