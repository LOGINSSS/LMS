package com.lms.auth.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lms.auth.auth.domain.po.LoginLog;
import org.apache.ibatis.annotations.Mapper;

/**
 * 登录日志 Mapper：操作 login_log 表，MyBatis-Plus BaseMapper 提供通用 CRUD
 */
@Mapper
public interface LoginLogMapper extends BaseMapper<LoginLog> {
}
