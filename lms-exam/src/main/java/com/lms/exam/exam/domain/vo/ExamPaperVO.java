package com.lms.exam.exam.domain.vo;

import lombok.Data;

import java.util.List;

/**
 * 试卷快照 VO（v1 收尾：出卷流程骨干）
 *
 * 教师视角（adminView）items 含 answer/analysis；学生视角（studentView）服务端剥离 answer/analysis 后下发。
 */
@Data
public class ExamPaperVO {

    private Long id;
    private String title;
    private String description;
    private Long courseId;
    private Long teacherId;
    private Integer status;
    private Integer totalScore;
    private List<ExamPaperItemVO> items;
}
