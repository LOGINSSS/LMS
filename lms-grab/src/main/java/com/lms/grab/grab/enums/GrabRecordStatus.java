package com.lms.grab.grab.enums;

import com.lms.common.enums.BaseEnum;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 抢课记录状态枚举
 *
 * 业务含义：抢课记录的生命周期——Redis 预检成功后先落一条待落库，
 * Kafka 消费端幂等落库 course_enrollment 后回写已落库；对账兜底回补。
 * 落库字段：grab_record.status（TINYINT）。
 */
@Getter
@AllArgsConstructor
public enum GrabRecordStatus implements BaseEnum {

    /** 成功待落库：Redis 已扣减，等待 Kafka 异步落库 */
    PENDING(1, "成功待落库"),

    /** 已落库：course_enrollment 已写入 */
    LANDED(2, "已落库"),

    /** 已回补：落库失败超时，库存已回补 */
    REFUNDED(3, "已回补");

    private final int value;
    private final String desc;
}
