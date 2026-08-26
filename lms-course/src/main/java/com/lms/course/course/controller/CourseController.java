package com.lms.course.course.controller;

import com.lms.common.domain.R;
import com.lms.common.domain.dto.PageDTO;
import com.lms.course.course.domain.dto.CourseCardVO;
import com.lms.course.course.domain.query.CoursePageQuery;
import com.lms.course.course.service.ICourseService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 课程前台接口（卡片展示 + 学生选课）
 *
 * 职责：接收前端请求、参数校验、调用服务并返回结果，不承载业务逻辑。
 */
@Tag(name = "课程前台接口")
@RestController
@RequestMapping("/courses")
@RequiredArgsConstructor
public class CourseController {

    private final ICourseService courseService;

    /** 课程卡片分页（只展示已发布课程，供前端渲染课程列表） */
    @GetMapping("/page")
    @Operation(summary = "课程卡片分页（已发布）")
    public R<PageDTO<CourseCardVO>> queryPage(CoursePageQuery query) {
        return R.ok(courseService.queryPublishedPage(query));
    }

    /** 课程卡片详情 */
    @GetMapping("/{id}")
    @Operation(summary = "课程卡片详情")
    public R<CourseCardVO> getDetail(@PathVariable("id") Long id) {
        return R.ok(courseService.getCourseDetail(id));
    }

    /** 学生选课（幂等，重复选课报业务错误） */
    @PostMapping("/{id}/enroll")
    @Operation(summary = "学生选课")
    public R<Void> enroll(@PathVariable("id") Long id) {
        courseService.enroll(id);
        return R.ok();
    }

    /** 学生退课 */
    @PostMapping("/{id}/quit")
    @Operation(summary = "学生退课")
    public R<Void> quit(@PathVariable("id") Long id) {
        courseService.quit(id);
        return R.ok();
    }

    /** 我选过的课程（学生视角，选课中状态） */
    @GetMapping("/enrolled")
    @Operation(summary = "我选过的课程")
    public R<PageDTO<CourseCardVO>> queryEnrolled(CoursePageQuery query) {
        return R.ok(courseService.queryEnrolledCourses(query));
    }
}
