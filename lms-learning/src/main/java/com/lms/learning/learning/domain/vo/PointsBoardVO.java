package com.lms.learning.learning.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 积分榜出参
 *
 * 业务含义：积分榜按用户聚合积分总分，用户名由前端按 userId 从用户服务补全（练手简化）。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "积分榜条目")
public class PointsBoardVO {

    /** 用户 id */
    private Long userId;

    /** 积分总分 */
    private Long totalPoints;
}
