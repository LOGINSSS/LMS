package com.lms.ai.tools;

import com.lms.ai.im.ImMessage;
import com.lms.ai.im.ImService;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * IM 工具组（spec §3.3/§7：im-agent 工具面）
 */
@Component
@RequiredArgsConstructor
public class ImTools {

    private final ImService imService;

    @Tool(name = "pushMessage", description = "推送文本消息到老师小助手（微信/QQ/企微）。推送时带 taskId/sessionId 便于老师回复后回流定位")
    public String pushMessage(
            @ToolParam(name = "to", description = "目标（默认 teacher-assistant）", required = false) String to,
            @ToolParam(name = "text", description = "消息内容") String text,
            @ToolParam(name = "taskId", description = "关联任务 id（可选）", required = false) String taskId,
            @ToolParam(name = "sessionId", description = "关联会话 id（可选）", required = false) String sessionId,
            RuntimeContext ctx) {
        Long userId = ToolSupport.enter(ctx);
        try {
            return ToolSupport.json(imService.push(new ImMessage(
                    to == null || to.isBlank() ? "teacher-assistant" : to, text, taskId, sessionId, userId)));
        } catch (Exception e) {
            return ToolSupport.fail(e);
        } finally {
            ToolSupport.exit();
        }
    }
}
