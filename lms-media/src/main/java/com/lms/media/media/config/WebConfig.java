package com.lms.media.media.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web MVC 配置
 *
 * 职责：把 /uploads/** 静态资源映射到本地磁盘上传目录，
 * 使媒资访问 URL（url-prefix + /uploads/ + 相对路径）可直接预览/播放/下载。
 * 上传目录来自 Nacos 配置 lms.media.upload-dir（本地磁盘绝对路径）。
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    /** 媒资文件根目录（本地磁盘绝对路径，来自 Nacos 配置 lms.media.upload-dir） */
    @Value("${lms.media.upload-dir}")
    private String uploadDir;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // 将 /uploads/** 静态映射到磁盘 upload-dir 目录，与落库的媒资 URL 一一对应
        registry.addResourceHandler("/uploads/**")
                .addResourceLocations("file:" + uploadDir + "/");
    }
}
