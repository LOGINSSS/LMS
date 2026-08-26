package com.lms.remark.remark.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lms.remark.remark.domain.po.LikedRecord;
import org.apache.ibatis.annotations.Mapper;

/**
 * 点赞记录数据访问：简单 CRUD 用 MyBatis-Plus 内置方法
 */
@Mapper
public interface LikedRecordMapper extends BaseMapper<LikedRecord> {
}
