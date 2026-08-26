package com.lms.course;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * 课程服务启动类
 *
 * 职责：课程卡片展示、教师建课/管理、学生选课。
 * 说明：@EnableFeignClients 扫描 client 包启用 Feign 客户端（调 lms-user 取教师昵称）；
 * @MapperScan 扫描 mapper 包注册 MyBatis-Plus Mapper。
 */
@SpringBootApplication
@EnableFeignClients(basePackages = "com.lms.course.course.client")
@MapperScan("com.lms.course.course.mapper")
public class CourseApplication {

    public static void main(String[] args) {
        SpringApplication.run(CourseApplication.class, args);
    }
}
