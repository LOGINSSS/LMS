package com.lms.remark;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 评价互动服务启动类
 *
 * 职责：跨课程/笔记/问答对象的通用点赞互动（点赞切换、点赞总数、点赞状态）。
 * 说明：@MapperScan 扫描 mapper 包注册 MyBatis-Plus Mapper。
 */
@SpringBootApplication
@MapperScan("com.lms.remark.remark.mapper")
public class RemarkApplication {

    public static void main(String[] args) {
        SpringApplication.run(RemarkApplication.class, args);
    }
}
