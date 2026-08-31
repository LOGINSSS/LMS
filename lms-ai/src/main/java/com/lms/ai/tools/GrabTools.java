package com.lms.ai.tools;

import com.lms.ai.client.GrabClient;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 抢课工具组（0.2 §7.1：course-agent/student-agent 工具面，Feign → lms-grab）
 *
 * 读操作（状态查询）公开；抢课（高副作用）需学生身份（lms-grab 侧校验）。
 */
@Component
@RequiredArgsConstructor
public class GrabTools {

    private final GrabClient client;

    @Tool(name = "grabStatus", description = "抢课状态（窗口/剩余库存/是否已抢）", readOnly = true)
    public String grabStatus(@ToolParam(name = "courseId", description = "课程 id") Long courseId) {
        try {
            return ToolSupport.json(ToolSupport.check(client.status(courseId)));
        } catch (Exception e) {
            return ToolSupport.fail(e);
        }
    }

    @Tool(name = "grabCourse", description = "抢课（学生，Redis 预检 + 异步落库）")
    public String grabCourse(@ToolParam(name = "courseId", description = "课程 id") Long courseId, RuntimeContext ctx) {
        ToolSupport.enter(ctx);
        try {
            return ToolSupport.json(ToolSupport.check(client.grab(courseId)));
        } catch (Exception e) {
            return ToolSupport.fail(e);
        } finally {
            ToolSupport.exit();
        }
    }
}
