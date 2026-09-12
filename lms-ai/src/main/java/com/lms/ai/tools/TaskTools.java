package com.lms.ai.tools;

import cn.hutool.json.JSONUtil;
import com.lms.ai.task.AgentTask;
import com.lms.ai.task.TaskService;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 任务工具组（spec §3.3/§6：task-agent 工具面）
 */
@Component
@RequiredArgsConstructor
public class TaskTools {

    private final TaskService taskService;

    @Tool(name = "schedule", description = "发布定时任务：延迟任务（delaySeconds 秒后执行）或一次性/周期任务。taskType 如 qa_remind；payloadJson 为 JSON 对象字符串")
    public String schedule(
            @ToolParam(name = "taskType", description = "任务类型（qa_remind/outline_generate/...）") String taskType,
            @ToolParam(name = "payloadJson", description = "任务参数 JSON 对象字符串（如 {\"question\":\"...\",\"sessionId\":\"1\"}）", required = false) String payloadJson,
            @ToolParam(name = "delaySeconds", description = "延迟秒数（延迟任务）", required = false) Integer delaySeconds,
            @ToolParam(name = "cron", description = "周期 cron（周期任务）", required = false) String cron,
            RuntimeContext ctx) {
        try {
            Long userId = ToolSupport.requireUser(ctx);
            Map<String, Object> payload = new HashMap<>();
            if (payloadJson != null && !payloadJson.isBlank()) {
                payload = JSONUtil.parseObj(payloadJson);
            }
            int triggerType = cron != null && !cron.isBlank() ? AgentTask.TRIGGER_CRON
                    : (delaySeconds != null ? AgentTask.TRIGGER_DELAY : AgentTask.TRIGGER_ONCE);
            String taskId = taskService.schedule(taskType, userId, "agent", triggerType, payload, delaySeconds, cron);
            return ToolSupport.json(Map.of("taskId", taskId,
                    "executeTime", taskService.queryOwned(taskId, userId).getExecuteTime()));
        } catch (Exception e) {
            return ToolSupport.fail(e);
        } finally {
            ToolSupport.exit();
        }
    }

    @Tool(name = "query", description = "查询任务状态与结果（taskId）", readOnly = true)
    public String query(@ToolParam(name = "taskId", description = "任务 id") String taskId,
                        RuntimeContext ctx) {
        try {
            Long userId = ToolSupport.requireUser(ctx);
            AgentTask t = taskService.queryOwned(taskId, userId);
            Map<String, Object> view = new LinkedHashMap<>();
            view.put("taskId", t.getTaskId());
            view.put("taskType", t.getTaskType());
            view.put("status", t.getStatus());
            view.put("result", t.getResult());
            view.put("errorMsg", t.getErrorMsg());
            view.put("executeTime", t.getExecuteTime());
            return ToolSupport.json(view);
        } catch (Exception e) {
            return ToolSupport.fail(e);
        } finally {
            ToolSupport.exit();
        }
    }

    @Tool(name = "cancel", description = "取消未执行任务（taskId）")
    public String cancel(@ToolParam(name = "taskId", description = "任务 id") String taskId,
                         RuntimeContext ctx) {
        try {
            Long userId = ToolSupport.requireUser(ctx);
            taskService.cancelOwned(taskId, userId);
            return "ok";
        } catch (Exception e) {
            return ToolSupport.fail(e);
        } finally {
            ToolSupport.exit();
        }
    }
}
