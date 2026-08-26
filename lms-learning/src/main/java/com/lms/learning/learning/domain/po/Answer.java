package com.lms.learning.learning.domain.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.lms.common.domain.po.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 互动问答-回答实体
 *
 * 对应表 answer。业务含义：问题的回答，回答发放回答积分，被采纳再发采纳奖励。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("answer")
public class Answer extends BaseEntity {

    /** 回答 id（自增主键） */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 问题 id（关联 question.id） */
    private Long questionId;

    /** 回答人（lms_user.user.id） */
    private Long userId;

    /** 回答内容 */
    private String content;

    /** 是否被采纳：1是 0否 */
    private Integer accepted;
}
