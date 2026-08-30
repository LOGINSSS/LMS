package com.lms.kb.knowledge.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 创建知识库入参（spec §4.4：归属从「仅课程」扩展为 owner_type + owner_id）
 *
 * 兼容旧调用：只传 courseId 时按课程知识库创建（ownerType=1，ownerId=courseId）；
 * 个人知识库：ownerType=2 + ownerId=userId（不传则取当前登录用户）。
 */
@Data
@Schema(description = "创建知识库入参")
public class KbFormDTO {

    @Schema(description = "课程 id（ownerType=1 课程知识库时必填）")
    private Long courseId;

    @Schema(description = "归属类型：1 课程 / 2 用户（个人知识库），默认 1")
    private Integer ownerType;

    @Schema(description = "归属 id：ownerType=2 时为用户 id（不传取当前登录用户）")
    private Long ownerId;

    @Schema(description = "知识库名称（默认取课程名）")
    private String name;
}
