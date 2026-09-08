package com.lms.exam.exam.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lms.exam.exam.domain.po.ExamSubmission;

/**
 * 考试提交 Mapper（Kafka 异步幂等落库）
 */
public interface ExamSubmissionMapper extends BaseMapper<ExamSubmission> {
}
