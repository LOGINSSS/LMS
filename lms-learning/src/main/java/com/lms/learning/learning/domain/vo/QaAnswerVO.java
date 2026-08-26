package com.lms.learning.learning.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 互动问答-回答出参
 */
@Data
@Schema(description = "问答回答信息")
public class QaAnswerVO {

    /** 回答 id */
    private Long id;

    /** 问题 id */
    private Long questionId;

    /** 回答人 */
    private Long userId;

    /** 回答内容 */
    private String content;

    /** 是否被采纳：1是 0否 */
    private Integer accepted;

    /** 创建时间 */
    private LocalDateTime createTime;
}
