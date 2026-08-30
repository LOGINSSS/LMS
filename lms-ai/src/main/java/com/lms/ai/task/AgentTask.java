package com.lms.ai.task;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.lms.common.domain.po.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * Agent 定时任务实体（spec §6.1：agent_task 表）
 *
 * 任务状态机：0待执行 → 1执行中 → 2完成 / 3失败 / 4取消。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("agent_task")
public class AgentTask extends BaseEntity {

    /** 任务 id（自增主键） */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 业务幂等键（UUID，task_id 唯一） */
    private String taskId;

    /** 归属用户（老师/学生） */
    private Long ownerId;

    /** 发起 agent 名 */
    private String agentName;

    /** 任务类型：qa_remind / outline_generate / report_xxx ... */
    private String taskType;

    /** 任务参数 JSON（问题、课程、回复地址...） */
    private String payload;

    /** 触发类型：1 延迟执行 / 2 周期执行 / 3 一次性 */
    private Integer triggerType;

    /** 延迟秒数（triggerType=1） */
    private Integer delaySeconds;

    /** 周期 cron（triggerType=2） */
    private String cron;

    /** 状态：0待执行 1执行中 2完成 3失败 4取消 */
    private Integer status;

    /** 执行结果（agent 输出） */
    private String result;

    /** 计划执行时间 */
    private LocalDateTime executeTime;

    /** 完成时间 */
    private LocalDateTime finishTime;

    /** 失败原因/重试信息 */
    private String errorMsg;

    /** 状态常量 */
    public static final int STATUS_PENDING = 0;
    public static final int STATUS_RUNNING = 1;
    public static final int STATUS_DONE = 2;
    public static final int STATUS_FAILED = 3;
    public static final int STATUS_CANCELED = 4;

    /** 触发类型常量 */
    public static final int TRIGGER_DELAY = 1;
    public static final int TRIGGER_CRON = 2;
    public static final int TRIGGER_ONCE = 3;
}
