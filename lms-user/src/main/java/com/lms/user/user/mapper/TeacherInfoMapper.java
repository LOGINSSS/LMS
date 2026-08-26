package com.lms.user.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lms.user.user.domain.po.TeacherInfo;
import org.apache.ibatis.annotations.Mapper;

/**
 * 教师扩展信息 Mapper（对应 lms_user.teacher_info）
 */
@Mapper
public interface TeacherInfoMapper extends BaseMapper<TeacherInfo> {
}
