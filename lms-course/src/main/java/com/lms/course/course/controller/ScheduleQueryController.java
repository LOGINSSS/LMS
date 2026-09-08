package com.lms.course.course.controller;

import com.lms.common.domain.R;
import com.lms.course.course.domain.vo.ClassEventVO;
import com.lms.course.course.service.IScheduleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 课程排课公开查询（课表/日历聚合用：按课程集 + 日期区间取上课事件）
 */
@Tag(name = "课程排课查询")
@RestController
@RequestMapping("/courses")
@RequiredArgsConstructor
public class ScheduleQueryController {

    private final IScheduleService scheduleService;

    @GetMapping("/schedule")
    @Operation(summary = "上课事件展开（courseIds=1,2 start/end 日期区间，周视图/日历用）")
    public R<List<ClassEventVO>> schedule(
            @RequestParam("courseIds") String courseIds,
            @RequestParam("start") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
            @RequestParam("end") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end) {
        List<Long> ids = Arrays.stream(courseIds.split(","))
                .map(String::trim).filter(s -> !s.isEmpty()).map(Long::valueOf)
                .collect(Collectors.toList());
        return R.ok(scheduleService.expand(ids, start, end));
    }
}
