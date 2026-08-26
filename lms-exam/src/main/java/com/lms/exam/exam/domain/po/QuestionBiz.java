package com.lms.exam.exam.domain.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.lms.common.domain.po.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 题目-业务绑定实体
 *
 * 对应表 question_biz。业务含义：题目归属到具体业务（课程/考试），
 * 同一题目在同一业务下唯一（uk_question_biz），可配置该业务下的分值。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("question_biz")
public class QuestionBiz extends BaseEntity {

    /** 绑定记录 id（自增主键） */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 题目 id（关联 question.id） */
    private Long questionId;

    /** 业务 id（课程/考试 id） */
    private Long bizId;

    /** 该业务下题目分值（组卷计分用） */
    private Integer score;
}
