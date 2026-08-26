package com.lms.statistics.statistics.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lms.common.domain.dto.PageDTO;
import com.lms.common.utils.AssertUtils;
import com.lms.statistics.statistics.client.CourseStatsClient;
import com.lms.statistics.statistics.client.LearningStatsClient;
import com.lms.statistics.statistics.client.UserStatsClient;
import com.lms.statistics.statistics.domain.po.DailyStats;
import com.lms.statistics.statistics.domain.vo.DashboardVO;
import com.lms.statistics.statistics.domain.vo.TodayStatsVO;
import com.lms.statistics.statistics.mapper.DailyStatsMapper;
import com.lms.statistics.statistics.service.IStatisticsService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;

/**
 * 数据中心业务服务实现
 *
 * 数据边界：本服务不持有业务明细，全部经 Feign 实时聚合 lms-user / lms-course / lms-learning；
 * 各维度调用失败由 fallback 降级为 0/空，保证看板可用性；
 * 今日数据聚合后 upsert 当日快照（daily_stats，uk_stat_date）。
 */
@Service
@RequiredArgsConstructor
public class StatisticsServiceImpl implements IStatisticsService {

    private final UserStatsClient userStatsClient;
    private final CourseStatsClient courseStatsClient;
    private final LearningStatsClient learningStatsClient;
    private final DailyStatsMapper dailyStatsMapper;

    @Override
    public DashboardVO overview() {
        //1. 登录校验：看板为管理视图，需登录用户
        AssertUtils.isNotNull(com.lms.common.utils.UserContext.getUser(), "请先登录");
        //2. 聚合各服务累计数据（每项独立调用，单项失败降级为 0，互不影响）
        Long userTotal = safeLong(userStatsClient.queryUserPage(1, 1));
        Long courseTotal = safeLong(courseStatsClient.queryCoursePage(1, 1));
        Long enrollTotal = safeLong(courseStatsClient.countEnrollTotal());
        Long learnTotal = safeLong(learningStatsClient.countLearnTotal());
        //3. 组装看板总览（积分榜 Top10 一并带入）
        DashboardVO vo = new DashboardVO();
        vo.setUserTotal(userTotal);
        vo.setCourseTotal(courseTotal);
        vo.setEnrollTotal(enrollTotal);
        vo.setLearnTotal(learnTotal);
        vo.setPointsBoard(Collections.unmodifiableList(topPoints(10)));
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TodayStatsVO today() {
        //1. 登录校验
        AssertUtils.isNotNull(com.lms.common.utils.UserContext.getUser(), "请先登录");
        //2. 聚合今日数据（新增用户/课程、签到、学习人次）
        Long todayUser = safeLong(userStatsClient.countTodayUsers());
        Long todayCourse = safeLong(courseStatsClient.countTodayCourses());
        Long todaySign = safeLong(learningStatsClient.countTodaySignIn());
        Long todayLearn = safeLong(learningStatsClient.countTodayLearn());
        //3. 写入当日快照：同一日期 upsert（uk_stat_date），含累计值便于历史对比
        saveDailySnapshot(todayUser, todayCourse, todaySign, todayLearn);
        //4. 组装返回
        TodayStatsVO vo = new TodayStatsVO();
        vo.setTodayUser(todayUser);
        vo.setTodayCourse(todayCourse);
        vo.setTodaySign(todaySign);
        vo.setTodayLearn(todayLearn);
        return vo;
    }

    @Override
    public List<Object> topCourses(int size) {
        AssertUtils.isNotNull(com.lms.common.utils.UserContext.getUser(), "请先登录");
        //1. 透传 lms-course 热度榜（失败降级为空列表）
        try {
            return Collections.unmodifiableList(courseStatsClient.topCourses(Math.max(1, size)).getData());
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    @Override
    public List<Object> topPoints(int size) {
        AssertUtils.isNotNull(com.lms.common.utils.UserContext.getUser(), "请先登录");
        //1. 透传 lms-learning 积分榜（失败降级为空列表）
        try {
            return Collections.unmodifiableList(learningStatsClient.pointsBoard(Math.max(1, size)).getData());
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    /**
     * 从分页响应取 total（用户/课程总数），响应为 null 时记 0
     */
    private Long safeLong(com.lms.common.domain.R<?> resp) {
        if (resp == null || resp.getData() == null) {
            return 0L;
        }
        if (resp.getData() instanceof PageDTO<?> page) {
            return page.getTotal();
        }
        if (resp.getData() instanceof Number num) {
            return num.longValue();
        }
        return 0L;
    }

    /**
     * upsert 当日统计快照：存在则更新，不存在则插入（uk_stat_date 兜底）
     */
    private void saveDailySnapshot(Long todayUser, Long todayCourse, Long todaySign, Long todayLearn) {
        LocalDate today = LocalDate.now();
        DailyStats existed = dailyStatsMapper.selectOne(new LambdaQueryWrapper<DailyStats>()
                .eq(DailyStats::getStatDate, today));
        // 快照同时记录当日累计值（复用今日聚合的 total 维度，看板历史对比用）
        Long userTotal = safeLong(userStatsClient.queryUserPage(1, 1));
        Long courseTotal = safeLong(courseStatsClient.queryCoursePage(1, 1));
        Long enrollTotal = safeLong(courseStatsClient.countEnrollTotal());
        Long learnTotal = safeLong(learningStatsClient.countLearnTotal());
        if (existed != null) {
            existed.setUserCount(userTotal);
            existed.setCourseCount(courseTotal);
            existed.setEnrollCount(enrollTotal);
            existed.setLearnCount(learnTotal);
            existed.setTodayUser(todayUser);
            existed.setTodayCourse(todayCourse);
            existed.setTodaySign(todaySign);
            existed.setTodayLearn(todayLearn);
            dailyStatsMapper.updateById(existed);
            return;
        }
        DailyStats stats = new DailyStats();
        stats.setStatDate(today);
        stats.setUserCount(userTotal);
        stats.setCourseCount(courseTotal);
        stats.setEnrollCount(enrollTotal);
        stats.setLearnCount(learnTotal);
        stats.setTodayUser(todayUser);
        stats.setTodayCourse(todayCourse);
        stats.setTodaySign(todaySign);
        stats.setTodayLearn(todayLearn);
        dailyStatsMapper.insert(stats);
    }
}
