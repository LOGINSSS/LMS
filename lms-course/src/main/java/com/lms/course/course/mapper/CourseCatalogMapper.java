package com.lms.course.course.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lms.course.course.domain.po.CourseCatalog;
import org.apache.ibatis.annotations.Mapper;

/**
 * 课程目录数据访问：简单 CRUD 用 MyBatis-Plus 内置方法
 */
@Mapper
public interface CourseCatalogMapper extends BaseMapper<CourseCatalog> {
}
