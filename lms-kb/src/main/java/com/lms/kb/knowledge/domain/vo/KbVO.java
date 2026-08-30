package com.lms.kb.knowledge.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 知识库视图
 */
@Data
@Schema(description = "知识库视图")
public class KbVO {

    private Long id;
    /** 归属类型：1 课程 / 2 用户 */
    private Integer ownerType;
    /** 归属 id */
    private Long ownerId;
    private Long courseId;
    private String name;
    private Integer status;
    private Integer docCount;
    private Integer chunkCount;
    private LocalDateTime createTime;
}
