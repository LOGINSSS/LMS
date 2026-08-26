package com.lms.course.course.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lms.course.course.domain.po.Course;
import org.apache.ibatis.annotations.Mapper;

/**
 * 课程数据访问：简单 CRUD 用 MyBatis-Plus 内置方法，复杂 SQL 走 resources/mapper
 */
@Mapper
public interface CourseMapper extends BaseMapper<Course> {
}
