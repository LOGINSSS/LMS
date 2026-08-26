package com.lms.search.search.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lms.search.search.domain.po.UserInterest;
import org.apache.ibatis.annotations.Mapper;

/**
 * 用户兴趣标签数据访问：简单 CRUD 用 MyBatis-Plus 内置方法
 */
@Mapper
public interface UserInterestMapper extends BaseMapper<UserInterest> {
}
