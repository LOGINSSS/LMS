package com.lms.course.course.controller;

import com.lms.common.domain.R;
import com.lms.common.domain.dto.PageDTO;
import com.lms.course.course.domain.dto.CourseCardVO;
import com.lms.course.course.domain.dto.CourseFormDTO;
import com.lms.course.course.domain.query.CoursePageQuery;
import com.lms.course.course.domain.vo.CourseTopVO;
import com.lms.course.course.service.ICourseService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
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

    /** 教师上下架自己创建的课程：status 取值见 CourseStatus 枚举 */
    @PutMapping("/{id}/status")
    @Operation(summary = "上下架课程")
    public R<Void> changeStatus(@PathVariable("id") Long id, @RequestParam("status") Integer status) {
        courseService.changeStatus(id, status);
        return R.ok();
    }

    /** 教师提交课程发布（设置抢课窗口与名额，进入待发布） */
    @PutMapping("/{id}/publish")
    @Operation(summary = "提交课程发布（设置抢课窗口）")
    public R<Void> publish(@PathVariable("id") Long id,
                           @RequestParam("grabStartTime") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime grabStartTime,
                           @RequestParam("grabEndTime") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime grabEndTime,
                           @RequestParam(value = "stock", defaultValue = "0") Integer stock) {
        courseService.publish(id, grabStartTime, grabEndTime, stock);
        return R.ok();
    }

    /** 我的课程（教师管理视角，含未发布） */
    @GetMapping("/mine")
    @Operation(summary = "我的课程")
    public R<PageDTO<CourseCardVO>> queryMine(CoursePageQuery query) {
        return R.ok(courseService.queryMyCourses(query));
    }

    /** 选课人次（数据中心看板聚合用） */
    @GetMapping("/stats/enroll-total")
    @Operation(summary = "选课人次")
    public R<Long> countEnrollTotal() {
        return R.ok(courseService.countEnrollTotal());
    }

    /** 今日新增课程数（数据中心看板聚合用） */
    @GetMapping("/stats/today")
    @Operation(summary = "今日新增课程数")
    public R<Long> countTodayCourses() {
        return R.ok(courseService.countTodayCourses());
    }

    /** 热门课程 Top N（按选课人数，数据中心看板用） */
    @GetMapping("/stats/top")
    @Operation(summary = "热门课程 Top N")
    public R<List<CourseTopVO>> topCourses(@RequestParam(value = "size", defaultValue = "10") Integer size) {
        return R.ok(courseService.topCourses(size));
    }
}
