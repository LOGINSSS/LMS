package com.lms.course.course.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 课程缓存配置属性（nacos-config/lms-course.yaml 的 lms.cache.*）
 */
@Data
@Component
@ConfigurationProperties(prefix = "lms.cache")
public class CourseCacheProperties {

    /** 课程详情缓存 TTL（分钟） */
    private Long courseDetailTtlMinutes = 30L;

    /** 章节大纲缓存 TTL（分钟） */
    private Long catalogTtlMinutes = 30L;

    /** 章节正文缓存 TTL（分钟） */
    private Long chapterTtlMinutes = 60L;

    /** 课程详情缓存 key 前缀 */
    private String courseDetailKeyPrefix = "course:detail";

    /** 章节大纲缓存 key 前缀 */
    private String catalogKeyPrefix = "course:catalog";

    /** 章节正文缓存 key 前缀 */
    private String chapterKeyPrefix = "chapter:content";

    public String courseDetailKey(Long courseId) {
        return courseDetailKeyPrefix + ":" + courseId;
    }

    public String catalogKey(Long courseId) {
        return catalogKeyPrefix + ":" + courseId;
    }

    public String chapterKey(Long chapterId) {
        return chapterKeyPrefix + ":" + chapterId;
    }
}
