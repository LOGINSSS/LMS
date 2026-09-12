package com.lms.ai;

import com.lms.ai.infrastructure.memory.ReMeJobClient;
import com.lms.ai.infrastructure.memory.outbox.SessionMemoryOutboxMapper;
import com.lms.ai.memory.AgentProfileJobMapper;
import com.lms.ai.memory.AgentUserBehaviorMapper;
import com.lms.ai.memory.AgentUserProfileMapper;
import com.lms.ai.session.AgentSessionMapper;
import com.lms.ai.task.AgentTaskMapper;
import org.apache.ibatis.annotations.Mapper;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.annotation.MapperScan;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AiApplicationMapperScanTest {

    @Test
    void mapperScanIncludesOnlyExplicitMyBatisMappers() {
        MapperScan mapperScan = AiApplication.class.getAnnotation(MapperScan.class);

        assertThat(mapperScan.annotationClass()).isEqualTo(Mapper.class);
        assertThat(ReMeJobClient.class.isAnnotationPresent(Mapper.class)).isFalse();
        assertThat(List.of(
                AgentSessionMapper.class,
                AgentProfileJobMapper.class,
                AgentUserBehaviorMapper.class,
                AgentUserProfileMapper.class,
                AgentTaskMapper.class,
                SessionMemoryOutboxMapper.class))
                .allSatisfy(type -> assertThat(type.isAnnotationPresent(Mapper.class)).isTrue());
    }
}
