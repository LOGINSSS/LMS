package com.lms.ai.controller;

import com.lms.ai.session.AgentSessionService;
import com.lms.ai.task.AgentTask;
import com.lms.ai.task.TaskService;
import com.lms.common.domain.R;
import com.lms.common.exceptions.ForbiddenException;
import com.lms.common.exceptions.UnauthorizedException;
import com.lms.common.utils.UserContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 任务接口（spec §8.3：/agent/tasks）
 */
@Tag(name = "Agent 任务")
@RestController
@RequestMapping("/agent/tasks")
@RequiredArgsConstructor
public class TaskController {

    private final TaskService taskService;
    private final AgentSessionService sessionService;

    @PostMapping
    @Operation(summary = "教师发布任务")
    public R<String> schedule(@RequestBody @Valid TaskScheduleRequest req) {
        Long teacherId = requireTeacher();
        String taskId = taskService.schedule(req.taskType(), teacherId, "teacher-agent",
                req.triggerType(), req.payload(), req.delaySeconds(), req.cron());
        return R.ok(taskId);
    }

    @GetMapping("/{taskId}")
    @Operation(summary = "任务状态与结果")
    public R<AgentTask> query(@PathVariable("taskId") String taskId) {
        return R.ok(taskService.queryOwned(taskId, requireUser()));
    }

    @PostMapping("/{taskId}/cancel")
    @Operation(summary = "取消任务")
    public R<Void> cancel(@PathVariable("taskId") String taskId) {
        taskService.cancelOwned(taskId, requireUser());
        return R.ok();
    }

    @GetMapping("/tree")
    @Operation(summary = "会话任务树（turn/邀请/写工具/管道节点，仅会话归属人）")
    public R<java.util.List<AgentTask>> tree(@RequestParam("sessionId") Long sessionId) {
        sessionService.getOwned(requireUser(), sessionId);
        return R.ok(taskService.tree(sessionId));
    }

    public record TaskScheduleRequest(@NotBlank(message = "任务类型不能为空") String taskType,
                                      Integer triggerType,
                                      java.util.Map<String, Object> payload,
                                      Integer delaySeconds,
                                      String cron) {
    }

    private Long requireUser() {
        Long userId = UserContext.getUser();
        if (userId == null) {
            throw new UnauthorizedException("未登录");
        }
        return userId;
    }

    private Long requireTeacher() {
        Long userId = requireUser();
        if (!Integer.valueOf(2).equals(UserContext.getUserType())) {
            throw new ForbiddenException("仅教师可发布任务");
        }
        return userId;
    }
}
