package com.lms.grab.grab.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lms.grab.grab.domain.po.GrabRecord;
import org.apache.ibatis.annotations.Mapper;

/**
 * 抢课记录数据访问：简单 CRUD 用 MyBatis-Plus 内置方法
 */
@Mapper
public interface GrabRecordMapper extends BaseMapper<GrabRecord> {
}
