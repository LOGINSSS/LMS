package com.lms.statistics.statistics.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

/**
 * 积分榜条目（跨服务传输对象，来自 lms-learning 积分榜）
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class PointsBoardDTO {

    /** 用户 id */
    private Long userId;

    /** 积分总分 */
    private Long totalPoints;
}
