package com.lms.learning;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * 学习过程服务启动类
 *
 * 职责：课次管理、学习记录、笔记、互动问答、积分（签到/学习/问答奖励）、签到。
 * 消息信箱（QA 答疑通知）：学生提问/教师回答时经 Feign 查询课程归属并落通知（见 client/config）。
 */
@SpringBootApplication
@MapperScan("com.lms.learning.learning.mapper")
@EnableFeignClients(basePackages = "com.lms.learning")
public class LearningApplication {

    public static void main(String[] args) {
        SpringApplication.run(LearningApplication.class, args);
    }
}
