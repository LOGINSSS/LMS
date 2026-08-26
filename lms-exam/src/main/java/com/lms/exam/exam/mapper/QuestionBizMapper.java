package com.lms.exam.exam.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lms.exam.exam.domain.po.QuestionBiz;
import org.apache.ibatis.annotations.Mapper;

/**
 * 题目-业务绑定数据访问：简单 CRUD 用 MyBatis-Plus 内置方法
 */
@Mapper
public interface QuestionBizMapper extends BaseMapper<QuestionBiz> {
}
