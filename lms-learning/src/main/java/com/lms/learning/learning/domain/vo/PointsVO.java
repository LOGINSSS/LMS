package com.lms.learning.learning.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 积分记录出参
 */
@Data
@Schema(description = "积分记录")
public class PointsVO {

    /** 积分记录 id */
    private Long id;

    /** 积分类型（取值见 PointsType 枚举） */
    private Integer type;

    /** 积分变动（正增负减） */
    private Integer points;

    /** 创建时间 */
    private LocalDateTime createTime;
}
