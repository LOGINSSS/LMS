package com.lms.course.course.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lms.course.course.domain.po.CourseEnrollment;
import org.apache.ibatis.annotations.Mapper;

/**
 * 选课关系数据访问：简单 CRUD 用 MyBatis-Plus 内置方法
 */
@Mapper
public interface CourseEnrollmentMapper extends BaseMapper<CourseEnrollment> {
}
