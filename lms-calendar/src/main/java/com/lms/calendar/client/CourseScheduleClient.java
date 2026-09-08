package com.lms.calendar.client;

import com.lms.common.domain.R;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.Map;

/**
 * lms-course 排课事件（课表：上课事件展开）
 */
@FeignClient(name = "lms-course", contextId = "calendarCourseClient")
public interface CourseScheduleClient {

    @GetMapping("/courses/schedule")
    R<List<Map<String, Object>>> schedule(
            @RequestParam("courseIds") String courseIds,
            @RequestParam("start") String start,
            @RequestParam("end") String end);
}
