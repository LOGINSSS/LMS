package com.lms.auth.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lms.auth.auth.domain.po.Account;
import org.apache.ibatis.annotations.Mapper;

/**
 * 登录账号 Mapper：操作 account 表，MyBatis-Plus BaseMapper 提供通用 CRUD
 */
@Mapper
public interface AccountMapper extends BaseMapper<Account> {
}
