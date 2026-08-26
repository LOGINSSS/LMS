package com.lms.user.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lms.user.user.domain.po.StudentInfo;
import org.apache.ibatis.annotations.Mapper;

/**
 * 学生扩展信息 Mapper（对应 lms_user.student_info）
 */
@Mapper
public interface StudentInfoMapper extends BaseMapper<StudentInfo> {
}
