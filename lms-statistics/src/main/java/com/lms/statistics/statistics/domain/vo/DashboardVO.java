package com.lms.statistics.statistics.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

/**
 * 数据看板总览出参
 *
 * 使用场景：管理端首页看板，聚合各服务累计数据。
 */
@Data
@Schema(description = "数据看板总览")
public class DashboardVO {

    /** 用户总数 */
    private Long userTotal;

    /** 课程总数（已发布） */
    private Long courseTotal;

    /** 选课人次 */
    private Long enrollTotal;

    /** 学习人次累计 */
    private Long learnTotal;

    /** 积分榜 Top10（userId + totalPoints） */
    private List<Object> pointsBoard;
}
