package com.lms.media;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 媒资服务启动类
 *
 * 职责：文件/视频上传与管理，本地磁盘存储，存储抽象可换 OSS。
 * 说明：@MapperScan 扫描 mapper 包注册 MyBatis-Plus Mapper；
 * 本服务不依赖 Feign，不与其他业务服务直接交互。
 */
@SpringBootApplication
@MapperScan("com.lms.media.media.mapper")
public class MediaApplication {

    public static void main(String[] args) {
        SpringApplication.run(MediaApplication.class, args);
    }
}
