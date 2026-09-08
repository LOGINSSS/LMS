package com.lms.calendar.client;

import com.lms.common.domain.R;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.Map;

/**
 * lms-exam 发布物排期（考试 bizType=1 / 作业 bizType=2，日历事件源）
 */
@FeignClient(name = "lms-exam", contextId = "calendarExamClient")
public interface ExamScheduleClient {

    @GetMapping("/exam-schedules/mine")
    R<List<Map<String, Object>>> mine(
            @RequestParam(value = "courseIds", required = false) String courseIds,
            @RequestParam(value = "bizType", required = false) Integer bizType);
}
