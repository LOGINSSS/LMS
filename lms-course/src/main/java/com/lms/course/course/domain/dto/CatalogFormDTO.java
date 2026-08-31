package com.lms.course.course.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 目录节点表单入参（教师增/改章节目录共用）
 *
 * 使用场景：POST/PUT /admin/courses/{courseId}/catalog。
 */
@Data
@Schema(description = "目录节点表单")
public class CatalogFormDTO {

    /** 父节点 id（0=顶级章） */
    @NotNull(message = "父节点不能为空")
    private Long parentId;

    /** 章/节名称 */
    @NotBlank(message = "名称不能为空")
    @Size(max = 100, message = "名称不能超过100字")
    private String name;

    /** 层级：1 章 / 2 节 */
    @NotNull(message = "层级不能为空")
    private Integer level;

    /** 排序（小在前，可选） */
    private Integer sort;
}
