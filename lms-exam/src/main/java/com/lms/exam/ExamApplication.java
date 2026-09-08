package com.lms.exam;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * 考试/题库服务启动类
 *
 * 职责：题目管理（单选/多选/判断）、题目与业务（课程/考试）绑定、按业务取题、
 * 试卷快照（出卷流程）、发布物排期（作业/考试）与作答回流（Feign → lms-learning）。
 */
@SpringBootApplication
@MapperScan("com.lms.exam.exam.mapper")
@EnableFeignClients(basePackages = "com.lms.exam")
public class ExamApplication {

    public static void main(String[] args) {
        SpringApplication.run(ExamApplication.class, args);
    }
}
