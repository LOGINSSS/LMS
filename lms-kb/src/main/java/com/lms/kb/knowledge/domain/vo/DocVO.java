package com.lms.kb.knowledge.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 知识库文档视图
 */
@Data
@Schema(description = "知识库文档视图")
public class DocVO {

    private Long id;
    private String fileName;
    private String fileType;
    private Integer chunkCount;
    /** 处理状态：0待解析 1解析中 2向量化中 3完成 4失败 */
    private Integer status;
    private String errorMsg;
    private LocalDateTime createTime;
}
