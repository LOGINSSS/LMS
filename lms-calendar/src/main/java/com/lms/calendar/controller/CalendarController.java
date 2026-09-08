package com.lms.calendar.controller;

import com.lms.calendar.domain.vo.EventVO;
import com.lms.calendar.service.CalendarService;
import com.lms.common.domain.R;
import com.lms.common.utils.UserContext;
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
 * 统一日历接口（主页大日历 / 课表周视图）
 *
 * 前端传已报名课程 ids（courseIds），服务端聚合：上课(class) + 考试(exam) + 作业(assignment)，
 * 返回多色事件（EventVO），跳转 path 由前端 router.push。
 */
@Tag(name = "统一日历")
@RestController
@RequestMapping("/calendar")
@RequiredArgsConstructor
public class CalendarController {

    private final CalendarService calendarService;

    @GetMapping("/mine")
    @Operation(summary = "我的日历事件（courseIds=已报名课程；start/end yyyy-MM-dd 含端点；type 多色渲染）")
    public R<List<EventVO>> mine(
            @RequestParam("courseIds") String courseIds,
            @RequestParam("start") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
            @RequestParam("end") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end) {
        List<Long> ids = Arrays.stream(courseIds.split(","))
                .map(String::trim).filter(s -> !s.isEmpty()).map(Long::valueOf)
                .collect(Collectors.toList());
        return R.ok(calendarService.mine(UserContext.getUser(), ids, start, end));
    }
}
