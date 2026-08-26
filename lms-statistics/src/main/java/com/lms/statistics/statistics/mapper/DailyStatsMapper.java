package com.lms.statistics.statistics.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lms.statistics.statistics.domain.po.DailyStats;
import org.apache.ibatis.annotations.Mapper;

/**
 * 每日统计快照数据访问
 */
@Mapper
public interface DailyStatsMapper extends BaseMapper<DailyStats> {
}
