package com.lms.ai.infrastructure.memory.outbox;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.lms.common.domain.po.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/** Durable delivery record for one closed session's ReMe Auto Memory request. */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("agent_memory_outbox")
public class SessionMemoryOutbox extends BaseEntity {

    public static final int STATUS_PENDING = 0;
    public static final int STATUS_PROCESSING = 1;
    public static final int STATUS_DONE = 2;
    public static final int STATUS_RETRY = 3;
    public static final int STATUS_DEAD = 4;

    @TableId(type = IdType.AUTO)
    private Long id;
    private String eventId;
    private Long userId;
    private Long sessionId;
    private String payload;
    private Integer status;
    private Integer attempts;
    private LocalDateTime nextAttemptTime;
    private LocalDateTime finishTime;
    private String lastErrorType;
}
