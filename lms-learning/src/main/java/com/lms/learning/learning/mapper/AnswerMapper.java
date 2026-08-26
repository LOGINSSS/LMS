package com.lms.learning.learning.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lms.learning.learning.domain.po.Answer;
import org.apache.ibatis.annotations.Mapper;

/**
 * 问答回答数据访问
 */
@Mapper
public interface AnswerMapper extends BaseMapper<Answer> {
}
