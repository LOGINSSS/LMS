package com.lms.grab.grab.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 抢课状态出参（课程详情页/列表页展示）
 */
@Data
@Schema(description = "抢课状态")
public class GrabStatusVO {

    /** 课程 id */
    private Long courseId;

    /** 抢课开始时间 */
    private String grabStartTime;

    /** 抢课结束时间 */
    private String grabEndTime;

    /** 剩余库存（null=未预热/不限） */
    private Integer leftStock;

    /** 是否已抢 */
    private Boolean grabbed;
}
