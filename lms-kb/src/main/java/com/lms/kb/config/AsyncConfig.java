package com.lms.kb.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * 异步执行器：文档入库管道线程池
 */
@Configuration
public class AsyncConfig {

    @Bean("kbPipelineExecutor")
    public Executor kbPipelineExecutor(KbProperties kbProperties) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(kbProperties.getPipelineThreads());
        executor.setMaxPoolSize(kbProperties.getPipelineThreads() * 2);
        executor.setQueueCapacity(200);
        executor.setThreadNamePrefix("kb-pipeline-");
        executor.initialize();
        return executor;
    }
}
