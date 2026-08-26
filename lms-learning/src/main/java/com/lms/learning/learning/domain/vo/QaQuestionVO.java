package com.lms.learning.learning.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 互动问答-问题出参（含回答列表）
 */
@Data
@Schema(description = "问答问题信息")
public class QaQuestionVO {

    /** 问题 id */
    private Long id;

    /** 提问人 */
    private Long userId;

    /** 所属课程 id */
    private Long courseId;

    /** 问题标题 */
    private String title;

    /** 问题详情 */
    private String content;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 该问题的回答列表 */
    private List<QaAnswerVO> answers;
}
