package com.lms.learning.learning.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lms.learning.learning.domain.po.Lesson;
import org.apache.ibatis.annotations.Mapper;

/**
 * 课次数据访问
 */
@Mapper
public interface LessonMapper extends BaseMapper<Lesson> {
}
