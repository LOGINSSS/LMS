package com.lms.statistics.statistics.client;

import com.lms.common.domain.R;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

/**
 * lms-learning 学习统计降级工厂
 *
 * 业务规则：拉取失败返回 0/空列表，本次聚合该维度记 0，不阻塞看板。
 */
@Slf4j
@Component
public class LearningStatsClientFallbackFactory implements FallbackFactory<LearningStatsClient> {

    @Override
    public LearningStatsClient create(Throwable cause) {
        log.warn("调用 lms-learning 统计失败：{}", cause == null ? "未知原因" : cause.getMessage());
        return new LearningStatsClient() {
            @Override
            public R<List<PointsBoardDTO>> pointsBoard(Integer size) {
                return R.ok(Collections.emptyList());
            }

            @Override
            public R<Long> countTodaySignIn() {
                return R.ok(0L);
            }

            @Override
            public R<Long> countTodayLearn() {
                return R.ok(0L);
            }

            @Override
            public R<Long> countLearnTotal() {
                return R.ok(0L);
            }
        };
    }
}
