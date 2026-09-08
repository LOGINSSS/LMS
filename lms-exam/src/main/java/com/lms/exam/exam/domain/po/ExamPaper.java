package com.lms.exam.exam.domain.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.lms.common.domain.po.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 试卷快照实体（v1 收尾：出卷流程骨干，作业/考试 rails 共用，对应表 exam_paper）
 *
 * 语义：老师组卷时把题库题目快照进卷（内容/答案服务端留存），学生按卷答题只拿题干渲染，
 * 判分在服务端确定性完成（PaperGrader），试卷本身不直接暴露题库。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("exam_paper")
public class ExamPaper extends BaseEntity {

    public static final int STATUS_DRAFT = 0;
    public static final int STATUS_PUBLISHED = 1;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 卷面标题 */
    private String title;

    /** 卷面说明 */
    private String description;

    /** 关联课程 id（作业/考试场景） */
    private Long courseId;

    /** 组卷老师 id */
    private Long teacherId;

    /** 卷面总分（发布时汇总） */
    private Integer totalScore;

    /** 状态：0草稿 1已发布 */
    private Integer status;
}
