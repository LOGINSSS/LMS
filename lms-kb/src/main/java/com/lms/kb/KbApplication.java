package com.lms.kb;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * 知识库服务启动类
 *
 * 职责：多模态文档入库（docx/pptx/pdf/图片）→ ES 混合检索 → RAG 五步流水线
 * （rewrite/HyDE/retrieve/rerank/generate，AgentScope 模型调用）→ RAGAS 评估采集。
 * 每个课程一个知识库（knowledge_base.course_id 唯一），切片带 course_id 实现隔离。
 */
@SpringBootApplication
@EnableAsync
@ConfigurationPropertiesScan("com.lms.kb.config")
@EnableFeignClients(basePackages = "com.lms.kb.client")
@MapperScan("com.lms.kb.knowledge.mapper")
public class KbApplication {

    public static void main(String[] args) {
        SpringApplication.run(KbApplication.class, args);
    }
}
