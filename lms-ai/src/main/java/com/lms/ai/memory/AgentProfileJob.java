package com.lms.ai.memory;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 画像生成任务（spec §4.3：L2 摘要的异步计算，周期执行）
 */
@Data
@TableName("agent_profile_job")
public class AgentProfileJob {

    /** 主键 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 用户 id */
    private Long userId;

    /** 状态：0 待执行 1 执行中 2 完成 3 失败 */
    private Integer status;

    /** 上次摘要时间 */
    private LocalDateTime lastSummaryTime;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;

    /** 状态常量 */
    public static final int STATUS_PENDING = 0;
    public static final int STATUS_RUNNING = 1;
    public static final int STATUS_DONE = 2;
    public static final int STATUS_FAILED = 3;
}
