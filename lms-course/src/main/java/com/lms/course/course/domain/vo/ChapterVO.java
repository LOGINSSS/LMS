package com.lms.course.course.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 课程章节正文出参（右栏 markdown 片段）
 *
 * 使用场景：GET /catalog/{catalogId}/chapter 按目录节点查正文。
 */
@Data
@Schema(description = "课程章节正文")
public class ChapterVO {

    /** 目录节点 id */
    private Long catalogId;

    /** 章/节名称（冗余自目录，展示用） */
    private String name;

    /** markdown 正文 */
    private String contentMd;

    /** 字数 */
    private Integer wordCount;
}
