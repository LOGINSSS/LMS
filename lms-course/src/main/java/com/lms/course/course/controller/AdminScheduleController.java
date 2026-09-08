package com.lms.course.course.controller;

import com.lms.common.domain.R;
import com.lms.course.course.domain.dto.SlotForm;
import com.lms.course.course.domain.po.CourseScheduleSlot;
import com.lms.course.course.service.IScheduleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 课程排课管理接口（教师本人课程：添加模板 / 列表 / 删除）
 */
@Tag(name = "课程排课管理接口（教师）")
@RestController
@RequestMapping("/admin/courses")
@RequiredArgsConstructor
public class AdminScheduleController {

    private final IScheduleService scheduleService;

    @PostMapping("/{courseId}/slots")
    @Operation(summary = "添加排课模板（教师本人课程；每周/单周/双周/单次）")
    public R<Void> addSlot(@PathVariable("courseId") Long courseId,
                           @RequestBody @Valid SlotForm form) {
        scheduleService.addSlot(courseId, form);
        return R.ok();
    }

    @GetMapping("/{courseId}/slots")
    @Operation(summary = "排课模板列表（教师管理视图）")
    public R<List<CourseScheduleSlot>> listTemplates(@PathVariable("courseId") Long courseId) {
        return R.ok(scheduleService.listTemplates(courseId));
    }

    @DeleteMapping("/{courseId}/slots/{slotId}")
    @Operation(summary = "删除排课模板（教师本人课程）")
    public R<Void> removeSlot(@PathVariable("courseId") Long courseId,
                              @PathVariable("slotId") Long slotId) {
        scheduleService.removeSlot(courseId, slotId);
        return R.ok();
    }
}
