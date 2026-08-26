package com.lms.search.search.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 兴趣标签上报入参
 *
 * 使用场景：选课/浏览等行为后由前端调用 POST /interests/record 上报课程分类标签。
 */
@Data
@Schema(description = "兴趣标签上报")
public class TagFormDTO {

    /** 兴趣标签（如课程分类：微服务/前端/数据库） */
    @NotBlank(message = "标签不能为空")
    @Size(max = 50, message = "标签不能超过50字")
    private String tag;
}
