package com.lms.user.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lms.user.user.domain.po.User;
import org.apache.ibatis.annotations.Mapper;

/**
 * 用户档案 Mapper（对应 lms_user.user）
 */
@Mapper
public interface UserMapper extends BaseMapper<User> {
}
