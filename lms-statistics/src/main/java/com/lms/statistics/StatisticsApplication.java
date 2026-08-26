package com.lms.statistics;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * 数据中心服务启动类
 *
 * 职责：数据看板、今日数据、Top10 排行榜；跨服务 Feign 实时聚合 + 每日快照落库。
 * 说明：@EnableFeignClients 扫描 client 包启用 Feign 客户端。
 */
@SpringBootApplication
@EnableFeignClients(basePackages = "com.lms.statistics.statistics.client")
@MapperScan("com.lms.statistics.statistics.mapper")
public class StatisticsApplication {

    public static void main(String[] args) {
        SpringApplication.run(StatisticsApplication.class, args);
    }
}
