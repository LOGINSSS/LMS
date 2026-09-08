package com.lms.exam.exam.domain.vo;

import lombok.Data;

/**
 * 试卷快照明细 VO
 */
@Data
public class ExamPaperItemVO {

    private Integer seq;
    private Long questionId;
    private String stem;
    private Integer type;
    private String category;
    private Integer difficulty;
    private Integer score;

    /** 答案快照（仅教师视角下发；学生视角为 null） */
    private String answer;

    /** 解析快照（仅教师视角下发；学生视角为 null） */
    private String analysis;
}
