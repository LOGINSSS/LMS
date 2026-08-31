package com.lms.grab.grab.domain.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.lms.common.domain.po.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 抢课记录实体
 *
 * 对应表 grab_record。业务含义：抢课成功后的异步落库审计记录——
 * Redis 预检成功后先记一条（status=1 待落库），Kafka 消费端幂等落库
 * course_enrollment 后回写 status=2；对账任务兜底（spec 0.2 §4.5）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("grab_record")
public class GrabRecord extends BaseEntity {

    /** 抢课记录 id（自增主键） */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 课程 id */
    private Long courseId;

    /** 抢课用户（lms_user.user.id） */
    private Long userId;

    /** 抢课时间 */
    private LocalDateTime grabTime;

    /** 来源：1 抢课 */
    private Integer source;

    /** 状态：1 成功待落库 / 2 已落库 / 3 已回补 */
    private Integer status;
}
