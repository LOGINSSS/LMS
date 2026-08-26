package com.lms.learning.learning.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lms.learning.learning.domain.po.SignIn;
import org.apache.ibatis.annotations.Mapper;

/**
 * 签到数据访问
 */
@Mapper
public interface SignInMapper extends BaseMapper<SignIn> {
}
