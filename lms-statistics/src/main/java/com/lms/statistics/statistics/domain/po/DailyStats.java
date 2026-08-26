package com.lms.statistics.statistics.domain.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.lms.common.domain.po.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;

/**
 * 每日统计快照实体
 *
 * 对应表 daily_stats。业务含义：数据中心按日汇总的各域统计值快照，
 * 同一日期唯一（uk_stat_date），每次看板聚合后 upsert。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("daily_stats")
public class DailyStats extends BaseEntity {

    /** 快照 id（自增主键） */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 统计日期 */
    private LocalDate statDate;

    /** 用户总数 */
    private Long userCount;

    /** 课程总数（已发布） */
    private Long courseCount;

    /** 选课人次 */
    private Long enrollCount;

    /** 学习人次累计 */
    private Long learnCount;

    /** 今日新增用户 */
    private Long todayUser;

    /** 今日新增课程 */
    private Long todayCourse;

    /** 今日签到人数 */
    private Long todaySign;

    /** 今日学习人次 */
    private Long todayLearn;
}
