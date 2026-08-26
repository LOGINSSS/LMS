package com.lms.statistics.statistics.service;

import com.lms.statistics.statistics.domain.vo.DashboardVO;
import com.lms.statistics.statistics.domain.vo.TodayStatsVO;

import java.util.List;

/**
 * 数据中心业务服务
 *
 * 承载数据看板总览、今日数据、热门课程 Top、积分榜 Top 的跨服务实时聚合，
 * 各维度降级兜底（单服务故障记 0），保证看板可用。
 */
public interface IStatisticsService {

    /**
     * 数据看板总览（用户/课程/选课/学习累计 + 积分榜 Top10）
     *
     * @return 看板总览
     */
    DashboardVO overview();

    /**
     * 今日数据（今日新增用户/课程、今日签到/学习人次），并写入当日快照
     *
     * @return 今日数据
     */
    TodayStatsVO today();

    /**
     * 热门课程 Top N（按选课人数，透传 lms-course 热度榜）
     *
     * @param size 返回条数
     * @return 热门课程列表
     */
    List<Object> topCourses(int size);

    /**
     * 积分榜 Top N（透传 lms-learning 积分榜）
     *
     * @param size 返回条数
     * @return 积分榜列表
     */
    List<Object> topPoints(int size);
}
