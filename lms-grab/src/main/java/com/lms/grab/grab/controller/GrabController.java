package com.lms.grab.grab.controller;

import com.lms.common.domain.R;
import com.lms.grab.grab.domain.dto.GrabStatusVO;
import com.lms.grab.grab.service.IGrabService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 抢课接口（学生端 + 状态查询）
 */
@Tag(name = "抢课接口")
@RestController
@RequestMapping("/grab")
@RequiredArgsConstructor
public class GrabController {

    private final IGrabService grabService;

    /** 学生抢课（Redis 预检 + Kafka 异步落库） */
    @PostMapping("/{courseId}")
    @Operation(summary = "抢课")
    public R<Long> grab(@PathVariable("courseId") Long courseId) {
        return R.ok(grabService.grab(courseId));
    }

    /** 抢课状态（窗口/剩余库存/是否已抢） */
    @GetMapping("/{courseId}/status")
    @Operation(summary = "抢课状态")
    public R<GrabStatusVO> status(@PathVariable("courseId") Long courseId) {
        return R.ok(grabService.status(courseId));
    }
}
