package com.lms.grab.grab.controller;

import com.lms.common.domain.R;
import com.lms.grab.grab.service.IGrabService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 抢课内部接口（供 lms-course 发布后 Feign 调用预热库存）
 */
@Tag(name = "抢课内部接口（服务间）")
@RestController
@RequestMapping("/grab/internal")
@RequiredArgsConstructor
public class GrabInternalController {

    private final IGrabService grabService;

    /** 库存预热：课程发布成功后调用（幂等，重复预热重置库存） */
    @PostMapping("/courses/{courseId}/prepare")
    @Operation(summary = "库存预热（服务间）")
    public R<Void> prepare(@PathVariable("courseId") Long courseId,
                           @RequestParam("grabStartTime") String grabStartTime,
                           @RequestParam("grabEndTime") String grabEndTime,
                           @RequestParam(value = "stock", defaultValue = "0") Integer stock) {
        grabService.prepare(courseId, grabStartTime, grabEndTime, stock);
        return R.ok();
    }
}
