package com.lms.grab.grab.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 抢课配置属性（nacos-config/lms-grab.yaml 的 lms.grab.*）
 */
@Data
@Component
@ConfigurationProperties(prefix = "lms.grab")
public class GrabProperties {

    /** 抢课成功事件 Kafka topic（lms-course 消费端异步落库） */
    private String topic = "lms-grab-success";

    /** 抢课窗口 Redis key 前缀 */
    private String windowKeyPrefix = "grab:window";

    /** 库存 Redis key 前缀 */
    private String stockKeyPrefix = "grab:stock";

    /** 已抢用户集合 Redis key 前缀 */
    private String usersKeyPrefix = "grab:users";

    /** 抢课明细 Hash Redis key 前缀 */
    private String detailKeyPrefix = "grab:detail";

    public String windowKey(Long courseId) {
        return windowKeyPrefix + ":" + courseId;
    }

    public String stockKey(Long courseId) {
        return stockKeyPrefix + ":" + courseId;
    }

    public String usersKey(Long courseId) {
        return usersKeyPrefix + ":" + courseId;
    }

    public String detailKey(Long courseId) {
        return detailKeyPrefix + ":" + courseId;
    }
}
