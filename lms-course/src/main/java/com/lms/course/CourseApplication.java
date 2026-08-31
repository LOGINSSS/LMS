package com.lms.course;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 课程服务启动类
 *
 * 职责：课程卡片展示、教师建课/管理、学生选课、课程内容（目录/章节）、发布状态机、
 * 抢课成功消息消费（Kafka 异步落库 course_enrollment）。
 * 说明：@EnableFeignClients 扫描 client 包启用 Feign 客户端（调 lms-user 取教师昵称、lms-grab 预热库存）；
 * @MapperScan 扫描 mapper 包注册 MyBatis-Plus Mapper；
 * @EnableScheduling 启用课程状态定时流转（待发布→抢课中→进行中）；
 * @EnableKafka 启用抢课成功事件消费。
 */
@SpringBootApplication
@EnableFeignClients(basePackages = "com.lms.course.course.client")
@MapperScan("com.lms.course.course.mapper")
@EnableScheduling
@EnableKafka
public class CourseApplication {

    public static void main(String[] args) {
        SpringApplication.run(CourseApplication.class, args);
    }
}
