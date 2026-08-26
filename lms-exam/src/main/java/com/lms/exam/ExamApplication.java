package com.lms.exam;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 考试/题库服务启动类
 *
 * 职责：题目管理（单选/多选/判断）、题目与业务（课程/考试）绑定、按业务取题。
 */
@SpringBootApplication
@MapperScan("com.lms.exam.exam.mapper")
public class ExamApplication {

    public static void main(String[] args) {
        SpringApplication.run(ExamApplication.class, args);
    }
}
