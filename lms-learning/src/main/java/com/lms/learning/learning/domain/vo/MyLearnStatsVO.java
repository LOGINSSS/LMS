package com.lms.learning.learning.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 我的学习统计出参
 *
 * 使用场景：GET /learn/stats/my，学生学习概览页聚合个人学习数据，
 * 各指标均为当前登录用户的累计值（逻辑删除自动过滤）。
 */
@Data
@Schema(description = "我的学习统计")
public class MyLearnStatsVO {

    /** 我的笔记数 */
    @Schema(description = "我的笔记数")
    private Long noteTotal;

    /** 我的提问数 */
    @Schema(description = "我的提问数")
    private Long qaTotal;

    /** 我的回答数 */
    @Schema(description = "我的回答数")
    private Long answerTotal;

    /** 累计签到天数 */
    @Schema(description = "累计签到天数")
    private Long signTotal;

    /** 累计积分（积分流水 points 求和，正负相抵；与积分榜同口径） */
    @Schema(description = "累计积分")
    private Long pointsTotal;
}
