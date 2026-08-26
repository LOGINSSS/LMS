package com.lms.statistics.statistics.client;

import com.lms.common.domain.R;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Collections;
import java.util.List;

/**
 * lms-learning 学习统计客户端
 *
 * 用途：数据中心拉取积分榜、今日签到、今日/累计学习人次；
 * 失败由 {@link LearningStatsClientFallbackFactory} 降级返回 0/空列表。
 */
@FeignClient(name = "lms-learning", fallbackFactory = LearningStatsClientFallbackFactory.class)
public interface LearningStatsClient {

    /** 积分榜 Top N */
    @GetMapping("/points/board")
    R<List<PointsBoardDTO>> pointsBoard(@RequestParam("size") Integer size);

    /** 今日签到人数 */
    @GetMapping("/points/stats/sign-today")
    R<Long> countTodaySignIn();

    /** 今日学习人次 */
    @GetMapping("/points/stats/learn-today")
    R<Long> countTodayLearn();

    /** 学习人次累计 */
    @GetMapping("/points/stats/learn-total")
    R<Long> countLearnTotal();
}
