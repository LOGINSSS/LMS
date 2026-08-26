package com.lms.learning.learning.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lms.learning.learning.domain.po.PointsRecord;
import org.apache.ibatis.annotations.Mapper;

/**
 * 积分记录数据访问
 */
@Mapper
public interface PointsRecordMapper extends BaseMapper<PointsRecord> {
}
