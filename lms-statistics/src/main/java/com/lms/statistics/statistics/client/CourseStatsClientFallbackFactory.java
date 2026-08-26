package com.lms.statistics.statistics.client;

import com.lms.common.domain.R;
import com.lms.common.domain.dto.PageDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

/**
 * lms-course 课程统计降级工厂
 *
 * 业务规则：拉取失败返回 0/空列表，本次聚合该维度记 0，不阻塞看板。
 */
@Slf4j
@Component
public class CourseStatsClientFallbackFactory implements FallbackFactory<CourseStatsClient> {

    @Override
    public CourseStatsClient create(Throwable cause) {
        log.warn("调用 lms-course 统计失败：{}", cause == null ? "未知原因" : cause.getMessage());
        return new CourseStatsClient() {
            @Override
            public R<PageDTO<CourseBriefDTO>> queryCoursePage(Integer pageNo, Integer pageSize) {
                return R.ok(PageDTO.of(0L, Collections.emptyList()));
            }

            @Override
            public R<Long> countEnrollTotal() {
                return R.ok(0L);
            }

            @Override
            public R<Long> countTodayCourses() {
                return R.ok(0L);
            }

            @Override
            public R<List<CourseTopDTO>> topCourses(Integer size) {
                return R.ok(Collections.emptyList());
            }
        };
    }
}
