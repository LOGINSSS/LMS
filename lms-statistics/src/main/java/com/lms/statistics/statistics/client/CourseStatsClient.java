package com.lms.statistics.statistics.client;

import com.lms.common.domain.R;
import com.lms.common.domain.dto.PageDTO;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Collections;
import java.util.List;

/**
 * lms-course 课程统计客户端
 *
 * 用途：数据中心拉取课程总数、选课人次、今日新增、热门课程 Top；
 * 失败由 {@link CourseStatsClientFallbackFactory} 降级返回 0/空列表。
 */
@FeignClient(name = "lms-course", fallbackFactory = CourseStatsClientFallbackFactory.class)
public interface CourseStatsClient {

    /**
     * 课程卡片分页（拿 total 作为已发布课程总数）
     *
     * @param pageNo   页码
     * @param pageSize 每页大小
     * @return 统一响应体，data 为课程分页
     */
    @GetMapping("/courses/page")
    R<PageDTO<CourseBriefDTO>> queryCoursePage(@RequestParam("pageNo") Integer pageNo,
                                               @RequestParam("pageSize") Integer pageSize);

    /** 选课人次 */
    @GetMapping("/admin/courses/stats/enroll-total")
    R<Long> countEnrollTotal();

    /** 今日新增课程数 */
    @GetMapping("/admin/courses/stats/today")
    R<Long> countTodayCourses();

    /** 热门课程 Top N */
    @GetMapping("/admin/courses/stats/top")
    R<List<CourseTopDTO>> topCourses(@RequestParam("size") Integer size);
}
