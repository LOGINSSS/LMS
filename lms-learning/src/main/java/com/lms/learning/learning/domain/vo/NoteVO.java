package com.lms.learning.learning.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 笔记出参
 */
@Data
@Schema(description = "笔记信息")
public class NoteVO {

    /** 笔记 id */
    private Long id;

    /** 笔记作者 */
    private Long userId;

    /** 课程 id */
    private Long courseId;

    /** 课次 id */
    private Long lessonId;

    /** 笔记内容 */
    private String content;

    /** 创建时间 */
    private LocalDateTime createTime;
}
