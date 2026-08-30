package com.lms.ai.tools;

import com.lms.ai.client.StatisticsClient;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 数据工具组（spec §3.3：statistics-agent 工具面，Feign → lms-statistics）
 */
@Component
@RequiredArgsConstructor
public class StatisticsTools {

    private final StatisticsClient client;

    @Tool(name = "dashboard", description = "平台数据看板（用户/课程/选课/学习/积分榜）", readOnly = true)
    public String dashboard(RuntimeContext ctx) {
        ToolSupport.enter(ctx);
        try {
            return ToolSupport.json(ToolSupport.check(client.dashboard()));
        } catch (Exception e) {
            return ToolSupport.fail(e);
        } finally {
            ToolSupport.exit();
        }
    }

    @Tool(name = "today", description = "今日新增数据（用户/课程/签到/学习）", readOnly = true)
    public String today(RuntimeContext ctx) {
        ToolSupport.enter(ctx);
        try {
            return ToolSupport.json(ToolSupport.check(client.today()));
        } catch (Exception e) {
            return ToolSupport.fail(e);
        } finally {
            ToolSupport.exit();
        }
    }

    @Tool(name = "topCourses", description = "课程热度榜 Top N", readOnly = true)
    public String topCourses(@ToolParam(name = "size", description = "条数，默认10", required = false) Integer size, RuntimeContext ctx) {
        ToolSupport.enter(ctx);
        try {
            return ToolSupport.json(ToolSupport.check(client.topCourses(size)));
        } catch (Exception e) {
            return ToolSupport.fail(e);
        } finally {
            ToolSupport.exit();
        }
    }

    @Tool(name = "topPoints", description = "积分榜 Top N", readOnly = true)
    public String topPoints(@ToolParam(name = "size", description = "条数，默认10", required = false) Integer size, RuntimeContext ctx) {
        ToolSupport.enter(ctx);
        try {
            return ToolSupport.json(ToolSupport.check(client.topPoints(size)));
        } catch (Exception e) {
            return ToolSupport.fail(e);
        } finally {
            ToolSupport.exit();
        }
    }
}
