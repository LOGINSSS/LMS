package com.lms.ai.client;

import com.lms.common.domain.R;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

/**
 * lms-statistics 数据中心服务 Feign 契约（spec §3.3：statistics-agent 工具面）
 */
@FeignClient(name = "lms-statistics", contextId = "statisticsClient")
public interface StatisticsClient {

    @GetMapping("/dashboard")
    R<Object> dashboard();

    @GetMapping("/dashboard/today")
    R<Object> today();

    @GetMapping("/dashboard/top/courses")
    R<List<Object>> topCourses(@RequestParam(value = "size", required = false) Integer size);

    @GetMapping("/dashboard/top/points")
    R<List<Object>> topPoints(@RequestParam(value = "size", required = false) Integer size);
}
