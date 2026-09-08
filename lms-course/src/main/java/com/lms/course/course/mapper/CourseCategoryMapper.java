package com.lms.course.course.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lms.course.course.domain.po.CourseCategory;
import org.apache.ibatis.annotations.Mapper;

/**
 * 课程分类标签数据访问：简单 CRUD 用 MyBatis-Plus 内置方法
 */
@Mapper
public interface CourseCategoryMapper extends BaseMapper<CourseCategory> {
}
