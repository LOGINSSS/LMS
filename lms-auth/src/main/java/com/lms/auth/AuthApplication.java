package com.lms.auth;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * 认证服务启动类
 *
 * 职责：承载注册 / 登录 / 登出 / 当前用户信息等认证能力，负责 JWT 签发与登出黑名单。
 *
 * 说明：
 * - @EnableFeignClients 扫描 client 包，启用 Feign 客户端（含 UserClientFallbackFactory 降级工厂）
 * - @MapperScan 扫描 mapper 包，注册 MyBatis-Plus Mapper
 */
@SpringBootApplication
@EnableFeignClients(basePackages = "com.lms.auth.auth.client")
@MapperScan("com.lms.auth.auth.mapper")
public class AuthApplication {

    public static void main(String[] args) {
        SpringApplication.run(AuthApplication.class, args);
    }
}
