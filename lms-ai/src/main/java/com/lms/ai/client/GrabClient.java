package com.lms.ai.client;

import com.lms.common.domain.R;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;

/**
 * lms-grab 抢课服务 Feign 契约（0.2 §7.1：抢课状态工具面）
 */
@FeignClient(name = "lms-grab", contextId = "grabClient")
public interface GrabClient {

    @GetMapping("/grab/{courseId}/status")
    R<Object> status(@PathVariable("courseId") Long courseId);

    @PostMapping("/grab/{courseId}")
    R<Long> grab(@PathVariable("courseId") Long courseId);
}
