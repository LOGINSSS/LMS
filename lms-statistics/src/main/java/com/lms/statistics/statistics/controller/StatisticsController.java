package com.lms.statistics.statistics.controller;

import com.lms.common.domain.R;
import com.lms.statistics.statistics.domain.vo.DashboardVO;
import com.lms.statistics.statistics.domain.vo.TodayStatsVO;
import com.lms.statistics.statistics.service.IStatisticsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 数据中心看板接口
 *
 * 职责：数据看板总览、今日数据、热门课程/积分榜 Top10，只接收参数并调用服务。
 */
@Tag(name = "数据中心看板接口")
@RestController
@RequestMapping("/dashboard")
@RequiredArgsConstructor
public class StatisticsController {

    private final IStatisticsService statisticsService;

    /** 数据看板总览（用户/课程/选课/学习累计 + 积分榜 Top10） */
    @GetMapping
    @Operation(summary = "数据看板总览")
    public R<DashboardVO> overview() {
        return R.ok(statisticsService.overview());
    }

    /** 今日数据（今日新增用户/课程、签到、学习人次，并写当日快照） */
    @GetMapping("/today")
    @Operation(summary = "今日数据")
    public R<TodayStatsVO> today() {
        return R.ok(statisticsService.today());
    }

    /** 热门课程 Top N（按选课人数） */
    @GetMapping("/top/courses")
    @Operation(summary = "热门课程 Top N")
    public R<List<Object>> topCourses(@RequestParam(value = "size", defaultValue = "10") Integer size) {
        return R.ok(statisticsService.topCourses(size));
    }

    /** 积分榜 Top N */
    @GetMapping("/top/points")
    @Operation(summary = "积分榜 Top N")
    public R<List<Object>> topPoints(@RequestParam(value = "size", defaultValue = "10") Integer size) {
        return R.ok(statisticsService.topPoints(size));
    }
}
