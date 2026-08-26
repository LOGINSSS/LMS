package com.lms.learning.learning.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lms.learning.learning.domain.po.LearningRecord;
import org.apache.ibatis.annotations.Mapper;

/**
 * 学习记录数据访问
 */
@Mapper
public interface LearningRecordMapper extends BaseMapper<LearningRecord> {
}
