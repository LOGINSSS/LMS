package com.lms.learning;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 学习过程服务启动类
 *
 * 职责：课次管理、学习记录、笔记、互动问答、积分（签到/学习/问答奖励）、签到。
 */
@SpringBootApplication
@MapperScan("com.lms.learning.learning.mapper")
public class LearningApplication {

    public static void main(String[] args) {
        SpringApplication.run(LearningApplication.class, args);
    }
}
