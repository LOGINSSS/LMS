package com.lms.learning.learning.controller;

import com.lms.common.domain.R;
import com.lms.common.domain.dto.PageDTO;
import com.lms.learning.learning.domain.vo.PointsBoardVO;
import com.lms.learning.learning.domain.vo.PointsVO;
import com.lms.learning.learning.service.ILearningService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 签到与积分接口
 *
 * 职责：每日签到、积分明细与积分榜。
 */
@Tag(name = "签到与积分接口")
@RestController
@RequestMapping("/points")
@RequiredArgsConstructor
public class PointsController {

    private final ILearningService learningService;

    /** 每日签到（一天一次，发放签到积分） */
    @PostMapping("/sign-in")
    @Operation(summary = "每日签到")
    public R<Void> signIn() {
        learningService.signIn();
        return R.ok();
    }

    /** 我的积分明细 */
    @GetMapping("/records")
    @Operation(summary = "我的积分明细")
    public R<PageDTO<PointsVO>> myPoints(@RequestParam(value = "pageNo", defaultValue = "1") Integer pageNo,
                                         @RequestParam(value = "pageSize", defaultValue = "10") Integer pageSize) {
        return R.ok(learningService.myPoints(pageNo, pageSize));
    }

    /** 积分榜 Top N */
    @GetMapping("/board")
    @Operation(summary = "积分榜")
    public R<List<PointsBoardVO>> board(@RequestParam(value = "size", defaultValue = "10") Integer size) {
        return R.ok(learningService.pointsBoard(size));
    }

    /** 今日签到人数（数据中心看板聚合用） */
    @GetMapping("/stats/sign-today")
    @Operation(summary = "今日签到人数")
    public R<Long> countTodaySignIn() {
        return R.ok(learningService.countTodaySignIn());
    }

    /** 今日学习人次（数据中心看板聚合用） */
    @GetMapping("/stats/learn-today")
    @Operation(summary = "今日学习人次")
    public R<Long> countTodayLearn() {
        return R.ok(learningService.countTodayLearn());
    }

    /** 学习人次累计（数据中心看板聚合用） */
    @GetMapping("/stats/learn-total")
    @Operation(summary = "学习人次累计")
    public R<Long> countLearnTotal() {
        return R.ok(learningService.countLearnTotal());
    }
}
