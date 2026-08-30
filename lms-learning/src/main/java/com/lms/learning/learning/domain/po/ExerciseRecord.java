package com.lms.learning.learning.domain.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.lms.common.domain.po.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 做题记录（错题集原料，评测业务线：习题/测评作答明细）
 *
 * 对应表 exercise_record。测评智能体/学生端上报，诊断智能体据此分析薄弱点（需求文档 §4.7）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("exercise_record")
public class ExerciseRecord extends BaseEntity {

    /** 主键 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 做题学生 */
    private Long userId;

    /** 课程 id */
    private Long courseId;

    /** 题目 id（lms_exam.question.id，0 表示测评临时题） */
    private Long questionId;

    /** 题型：1单选 2多选 3判断 */
    private Integer questionType;

    /** 对应知识点（题目 category） */
    private String knowledgePoint;

    /** 学生作答（JSON） */
    private String userAnswer;

    /** 是否答对：1对 0错 */
    private Integer correct;

    /** 本题得分 */
    private Integer score;

    /** 来源：1练习 2测评 */
    private Integer source;

    /** 来源常量 */
    public static final int SOURCE_EXERCISE = 1;
    public static final int SOURCE_ASSESSMENT = 2;
}
