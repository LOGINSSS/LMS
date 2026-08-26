package com.lms.search.search.domain.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.lms.common.domain.po.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 用户兴趣标签实体
 *
 * 对应表 user_interests。业务含义：记录用户对课程分类的兴趣（推荐依据），
 * 选课/浏览等行为上报时权重累加，同一用户同一标签唯一（uk_user_tag）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("user_interests")
public class UserInterest extends BaseEntity {

    /** 兴趣记录 id（自增主键） */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 用户 id（lms_user.user.id） */
    private Long userId;

    /** 兴趣标签（如课程分类：微服务/前端/数据库） */
    private String tag;

    /** 兴趣权重（上报行为累加，推荐时按权重排序） */
    private Integer weight;
}
