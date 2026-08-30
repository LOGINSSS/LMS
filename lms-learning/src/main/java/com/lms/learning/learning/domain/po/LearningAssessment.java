package com.lms.learning.learning.domain.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.lms.common.domain.po.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 测评结果（学习效果评估报告留存，需求文档 §4.5/§6.4 可观测性）
 *
 * 对应表 learning_assessment。测评智能体输出报告 + 知识点掌握度，供下一轮诊断迭代（闭环）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("learning_assessment")
public class LearningAssessment extends BaseEntity {

    /** 主键 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 被测评学生 */
    private Long userId;

    /** 课程 id */
    private Long courseId;

    /** 测评标题（如：数据结构-图 单元测评） */
    private String title;

    /** 评估报告（智能体输出，markdown/文本） */
    private String report;

    /** 总分 */
    private Integer totalScore;

    /** 知识点掌握度 JSON（{知识点: 掌握度0-100}） */
    private String knowledgeMastery;
}
