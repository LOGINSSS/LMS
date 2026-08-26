package com.lms.user;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 用户服务启动类（lms-user）
 *
 * 业务包结构：com.lms.user.user（constants / domain / mapper / service / controller）。
 * Mapper 扫描：com.lms.user.user.mapper 下的 MyBatis-Plus BaseMapper 接口。
 */
@SpringBootApplication
@MapperScan("com.lms.user.user.mapper")
public class UserApplication {

    public static void main(String[] args) {
        SpringApplication.run(UserApplication.class, args);
    }
}
