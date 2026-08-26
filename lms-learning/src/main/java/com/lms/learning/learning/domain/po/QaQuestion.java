package com.lms.learning.learning.domain.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.lms.common.domain.po.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 互动问答-问题实体
 *
 * 对应表 question。业务含义：课程内的学习提问，发起提问发放提问积分。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("question")
public class QaQuestion extends BaseEntity {

    /** 问题 id（自增主键） */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 提问人（lms_user.user.id） */
    private Long userId;

    /** 所属课程 id */
    private Long courseId;

    /** 问题标题 */
    private String title;

    /** 问题详情 */
    private String content;
}
