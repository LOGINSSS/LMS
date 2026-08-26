package com.lms.statistics.statistics.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 今日数据出参
 *
 * 使用场景：看板"今日"区块，聚合各服务当日新增/发生量。
 */
@Data
@Schema(description = "今日数据")
public class TodayStatsVO {

    /** 今日新增用户 */
    private Long todayUser;

    /** 今日新增课程 */
    private Long todayCourse;

    /** 今日签到人数 */
    private Long todaySign;

    /** 今日学习人次 */
    private Long todayLearn;
}
