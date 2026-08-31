package com.lms.grab;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 抢课服务启动类（0.2 新增模块）
 *
 * 职责：抢课高峰流量的 Redis 预检库存（Lua 原子扣减 + 去重）+ Kafka 异步落库；
 * 与课程 CRUD 解耦（spec 0.2 §4）。
 * 说明：@EnableFeignClients 扫描 client 包启用 Feign 客户端（调 lms-course 校验课程窗口）；
 * @MapperScan 扫描 mapper 包注册 MyBatis-Plus Mapper；
 * @EnableScheduling 启用库存预热/对账定时任务。
 */
@SpringBootApplication
@EnableFeignClients(basePackages = "com.lms.grab.grab.client")
@MapperScan("com.lms.grab.grab.mapper")
@EnableScheduling
public class GrabApplication {

    public static void main(String[] args) {
        SpringApplication.run(GrabApplication.class, args);
    }
}
