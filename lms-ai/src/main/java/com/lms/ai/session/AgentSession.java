package com.lms.ai.session;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.lms.common.domain.po.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * Agent 会话实体（spec §4.2：L1 会话管理）
 *
 * 对应表 agent_session：一次会话 = 一个 agent 实例的多轮对话上下文容器。
 * 会话消息本体存 Redis（agent:session:{sessionId}:messages，TTL 1~7 天），
 * 本表只存会话元数据（归属用户/agent 类型/标题/状态），用于"我的会话"列表与结束会话。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("agent_session")
public class AgentSession extends BaseEntity {

    /** 会话 id（自增主键，同时作为 Redis 消息 key 的一部分） */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 归属用户（lms-auth 用户 id） */
    private Long userId;

    /** agent 类型：student / teacher（决定用哪个个人 agent） */
    private String agentType;

    /** 会话标题（默认取首条消息前 N 字） */
    private String title;

    /** 状态：1 进行中 / 0 已结束 */
    private Integer status;

    /** 消息条数（冗余统计） */
    private Integer messageCount;

    /** 状态常量 */
    public static final int STATUS_ACTIVE = 1;
    public static final int STATUS_CLOSED = 0;
}
