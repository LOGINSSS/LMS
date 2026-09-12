package com.lms.ai.infrastructure.memory.outbox;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/** MyBatis-Plus mapper for durable memory delivery rows. */
@Mapper
public interface SessionMemoryOutboxMapper extends BaseMapper<SessionMemoryOutbox> {
}
