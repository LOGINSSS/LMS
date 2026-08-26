package com.lms.learning.learning.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lms.learning.learning.domain.po.QaQuestion;
import org.apache.ibatis.annotations.Mapper;

/**
 * 问答问题数据访问
 */
@Mapper
public interface QaQuestionMapper extends BaseMapper<QaQuestion> {
}
