package com.lms.exam.exam.domain.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.lms.common.domain.po.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 试卷快照明细实体（对应表 exam_paper_item）
 *
 * 语义：卷内每题一份快照（题干/答案/解析服务端留存）；answer 绝不下发学生端，
 * 学生提交后由服务端 PaperGrader 按 answer 快照确定性判分。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("exam_paper_item")
public class ExamPaperItem extends BaseEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 试卷 id */
    private Long paperId;

    /** 卷内序号（1 递增） */
    private Integer seq;

    /** 来源题库题目 id（0=非题库快照题，预留 AI 自测/KB 题） */
    private Long questionId;

    /** 题干（含选项文本快照） */
    private String stem;

    /** 题型：1单选 2多选 3判断 */
    private Integer type;

    /** 知识点/分类快照 */
    private String category;

    /** 难度：1易 2中 3难 */
    private Integer difficulty;

    /** 该题分值 */
    private Integer score;

    /** 答案快照（JSON，服务端判分用） */
    private String answer;

    /** 解析快照（讲解用） */
    private String analysis;
}
