package com.lms.ai.task;

import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.lms.ai.im.ImMessage;
import com.lms.ai.im.ImSendResult;
import com.lms.ai.im.ImService;
import com.lms.common.exceptions.CommonException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Agent 定时任务服务（spec §6：任务模型 + 落库轮询执行）
 *
 * - task.schedule 工具写入 agent_task（status=0）→ 发 Kafka 事件（best-effort，可选）
 * - 到点执行：TaskSchedulerRunner @Scheduled 扫描 execute_time<=now 的任务
 * - 补偿：失败 status=3 + errorMsg，超限可进死信（阶段 5 加固）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TaskService {

    /** 任务类型：学生问答 → 老师未回复提醒 */
    public static final String TYPE_QA_REMIND = "qa_remind";

    private final AgentTaskMapper taskMapper;
    private final ImService imService;
    private final TaskEventProducer eventProducer;

    /**
     * 发布任务（延迟/周期/一次性），返回 taskId
     *
     * @param taskType  任务类型
     * @param ownerId   归属用户
     * @param agentName 发起 agent
     * @param triggerType 1 延迟 / 2 cron / 3 一次性
     * @param payload   任务参数 JSON（Map）
     */
    @Transactional
    public String schedule(String taskType, Long ownerId, String agentName,
                           int triggerType, Map<String, Object> payload,
                           Integer delaySeconds, String cron) {
        AgentTask task = new AgentTask();
        task.setTaskId(IdUtil.fastSimpleUUID());
        task.setOwnerId(ownerId);
        task.setAgentName(agentName);
        task.setTaskType(taskType);
        task.setPayload(JSONUtil.toJsonStr(payload == null ? Map.of() : payload));
        task.setTriggerType(triggerType);
        task.setDelaySeconds(delaySeconds);
        task.setCron(cron);
        task.setStatus(AgentTask.STATUS_PENDING);
        task.setExecuteTime(computeExecuteTime(triggerType, delaySeconds, cron));
        taskMapper.insert(task);
        // Kafka 事件通知（best-effort，未启 Kafka 不影响落库执行）
        eventProducer.publish(task.getTaskId(), task.getExecuteTime());
        return task.getTaskId();
    }

    /** 查询任务状态与结果 */
    public AgentTask query(String taskId) {
        AgentTask task = taskMapper.selectOne(new LambdaQueryWrapper<AgentTask>()
                .eq(AgentTask::getTaskId, taskId));
        if (task == null) {
            throw new CommonException("任务不存在: " + taskId);
        }
        return task;
    }

    /** 取消未执行任务 */
    @Transactional
    public void cancel(String taskId) {
        taskMapper.update(null, new LambdaUpdateWrapper<AgentTask>()
                .eq(AgentTask::getTaskId, taskId)
                .eq(AgentTask::getStatus, AgentTask.STATUS_PENDING)
                .set(AgentTask::getStatus, AgentTask.STATUS_CANCELED)
                .set(AgentTask::getFinishTime, LocalDateTime.now()));
    }

    /**
     * 到点执行：status=0 且 execute_time<=now（TaskSchedulerRunner 调用）
     */
    public int executeDueTasks() {
        List<AgentTask> due = taskMapper.selectList(new LambdaQueryWrapper<AgentTask>()
                .eq(AgentTask::getStatus, AgentTask.STATUS_PENDING)
                .le(AgentTask::getExecuteTime, LocalDateTime.now()));
        int executed = 0;
        for (AgentTask task : due) {
            try {
                execute(task);
                executed++;
            } catch (Exception e) {
                log.error("任务执行失败 taskId={} type={}", task.getTaskId(), task.getTaskType(), e);
                taskMapper.update(null, new LambdaUpdateWrapper<AgentTask>()
                        .eq(AgentTask::getId, task.getId())
                        .set(AgentTask::getStatus, AgentTask.STATUS_FAILED)
                        .set(AgentTask::getErrorMsg, StrUtil.sub(e.getMessage(), 0, 500))
                        .set(AgentTask::getFinishTime, LocalDateTime.now()));
            }
        }
        return executed;
    }

    /** 执行单个任务（按 taskType 分发处理器；结果回写 + 通知发起方） */
    @Transactional
    public void execute(AgentTask task) {
        taskMapper.update(null, new LambdaUpdateWrapper<AgentTask>()
                .eq(AgentTask::getId, task.getId())
                .eq(AgentTask::getStatus, AgentTask.STATUS_PENDING)
                .set(AgentTask::getStatus, AgentTask.STATUS_RUNNING));
        String result = dispatch(task);
        taskMapper.update(null, new LambdaUpdateWrapper<AgentTask>()
                .eq(AgentTask::getId, task.getId())
                .set(AgentTask::getStatus, AgentTask.STATUS_DONE)
                .set(AgentTask::getResult, result)
                .set(AgentTask::getFinishTime, LocalDateTime.now()));
    }

    /** 任务分发：目前支持 qa_remind（学生问答 → 老师未回复提醒，spec §6.2 首个场景） */
    private String dispatch(AgentTask task) {
        Map<String, Object> payload = JSONUtil.parseObj(task.getPayload());
        switch (task.getTaskType()) {
            case TYPE_QA_REMIND -> {
                // 推送提醒给老师小助手（问题 + 学生上下文）
                String question = StrUtil.toString(payload.getOrDefault("question", ""));
                String studentName = StrUtil.toString(payload.getOrDefault("studentName", "学生"));
                String teacherTarget = StrUtil.toString(payload.getOrDefault("teacherTarget", "teacher-assistant"));
                String text = "【定时提醒】学生 " + studentName + " 的提问已超时未回复：\n" + question
                        + "\n请尽快在 IM 中回复（携带 taskId=" + task.getTaskId() + "）。";
                ImSendResult r = imService.push(new ImMessage(teacherTarget, text, task.getTaskId(),
                        StrUtil.toStringOrNull(payload.get("sessionId")), task.getOwnerId()));
                return "qa_remind 提醒推送: " + r.message();
            }
            default -> {
                // 其余类型：记录为完成（阶段 3+ 可接 agent 编排执行长任务）
                return "任务类型 " + task.getTaskType() + " 无处理器，标记完成（payload=" + task.getPayload() + "）";
            }
        }
    }

    private LocalDateTime computeExecuteTime(int triggerType, Integer delaySeconds, String cron) {
        if (triggerType == AgentTask.TRIGGER_DELAY) {
            return LocalDateTime.now().plusSeconds(delaySeconds == null ? 0 : delaySeconds);
        }
        return LocalDateTime.now();
    }
}
