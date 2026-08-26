package com.lms.search;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * 搜索服务启动类
 *
 * 职责：基于 Elasticsearch 的课程搜索与推荐、用户兴趣标签管理。
 * 说明：@EnableFeignClients 扫描 client 包启用 Feign 客户端（调 lms-course 同步索引）。
 */
@SpringBootApplication
@EnableFeignClients(basePackages = "com.lms.search.search.client")
@MapperScan("com.lms.search.search.mapper")
public class SearchApplication {

    public static void main(String[] args) {
        SpringApplication.run(SearchApplication.class, args);
    }
}
