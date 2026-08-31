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
 * 对应表 question_biz。业务含义：题目归属到具体业务（课程/章节/考试卷），
 * 同一题目在同一业务类型下唯一（uk_question_biz: question_id+biz_type+biz_id），
 * 可配置该业务下的分值。biz_type 取值见 BizType 枚举。
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

    /** 业务类型：1 课程 / 2 章节 / 3 考试卷（取值见 BizType 枚举） */
    private Integer bizType;

    /** 业务 id（bizType=1 课程 id / bizType=2 章节目录 id / bizType=3 考试卷 id） */
    private Long bizId;

    /** 该业务下题目分值（组卷计分用） */
    private Integer score;
}
