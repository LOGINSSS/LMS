package com.lms.course.course.client;

import com.lms.common.domain.R;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * lms-grab 抢课服务 Feign 契约（spec 0.2 §4.3）
 *
 * 用途：课程发布成功后调用预热库存（Redis 写入窗口 + 库存），
 * 学生抢课走网关 /grab/** 直达 lms-grab，不经本客户端。
 */
@FeignClient(name = "lms-grab", contextId = "grabClient", fallbackFactory = GrabClientFallbackFactory.class)
public interface GrabClient {

    /** 库存预热（幂等，重复预热重置库存） */
    @PostMapping("/grab/internal/courses/{courseId}/prepare")
    R<Void> prepare(@PathVariable("courseId") Long courseId,
                    @RequestParam("grabStartTime") String grabStartTime,
                    @RequestParam("grabEndTime") String grabEndTime,
                    @RequestParam("stock") Integer stock);
}
