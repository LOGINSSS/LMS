package com.lms.course.course.controller;

import com.lms.common.domain.R;
import com.lms.common.domain.dto.PageDTO;
import com.lms.course.course.domain.dto.CourseCardVO;
import com.lms.course.course.domain.dto.CourseFormDTO;
import com.lms.course.course.domain.query.CoursePageQuery;
import com.lms.course.course.service.ICourseService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 课程管理接口（教师端）
 *
 * 职责：教师创建/修改/上下架自己课程，只接收参数并调用服务，不承载业务逻辑。
 */
@Tag(name = "课程管理接口（教师端）")
@RestController
@RequestMapping("/admin/courses")
@RequiredArgsConstructor
public class AdminCourseController {

    private final ICourseService courseService;

    /** 教师添加课程（新课程默认下架，需发布后学生可见） */
    @PostMapping
    @Operation(summary = "教师添加课程")
    public R<Long> addCourse(@RequestBody @Valid CourseFormDTO dto) {
        return R.ok(courseService.addCourse(dto));
    }

    /** 教师修改自己创建的课程（仅更新传入字段） */
    @PutMapping("/{id}")
    @Operation(summary = "修改课程")
    public R<Void> updateCourse(@PathVariable("id") Long id, @RequestBody CourseFormDTO dto) {
        courseService.updateCourse(id, dto);
        return R.ok();
    }

    /** 教师上下架自己创建的课程：status=1 发布 / 0 下架 */
    @PutMapping("/{id}/status")
    @Operation(summary = "上下架课程")
    public R<Void> changeStatus(@PathVariable("id") Long id, @RequestParam("status") Integer status) {
        courseService.changeStatus(id, status);
        return R.ok();
    }

    /** 我的课程（教师管理视角，含未发布） */
    @GetMapping("/mine")
    @Operation(summary = "我的课程")
    public R<PageDTO<CourseCardVO>> queryMine(CoursePageQuery query) {
        return R.ok(courseService.queryMyCourses(query));
    }
}
