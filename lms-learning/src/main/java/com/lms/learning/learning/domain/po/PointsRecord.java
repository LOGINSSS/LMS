package com.lms.learning.learning.domain.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.lms.common.domain.po.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 积分记录实体
 *
 * 对应表 points_record。业务含义：签到/学习/问答等行为的积分流水，积分榜按此聚合。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("points_record")
public class PointsRecord extends BaseEntity {

    /** 积分记录 id（自增主键） */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 用户（lms_user.user.id） */
    private Long userId;

    /** 积分类型：1签到 2学习 3提问 4回答 5被采纳，取值见 PointsType 枚举 */
    private Integer type;

    /** 积分变动（正增负减） */
    private Integer points;
}
