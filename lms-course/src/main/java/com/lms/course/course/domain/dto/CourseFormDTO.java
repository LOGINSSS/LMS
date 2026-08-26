package com.lms.course.course.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 课程表单入参（教师添加/修改课程共用）
 *
 * 使用场景：POST /admin/courses 与 PUT /admin/courses/{id} 的请求体。
 * 修改场景下未传的字段不更新（服务层按 null 判断）。
 */
@Data
@Schema(description = "课程表单")
public class CourseFormDTO {

    /** 课程名称 */
    @NotBlank(message = "课程名称不能为空")
    @Size(max = 100, message = "课程名称不能超过100字")
    private String name;

    /** 封面图 URL */
    @Size(max = 255, message = "封面地址过长")
    private String cover;

    /** 课程简介（卡片文案展示） */
    @Size(max = 500, message = "课程简介不能超过500字")
    private String intro;

    /** 课程分类（如 微服务/前端/数据库） */
    @Size(max = 50, message = "分类不能超过50字")
    private String category;
}
