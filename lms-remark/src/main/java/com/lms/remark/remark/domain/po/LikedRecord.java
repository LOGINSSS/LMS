package com.lms.remark.remark.domain.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.lms.common.domain.po.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 点赞记录实体
 *
 * 对应表 liked_record。业务模型：同一用户对同一对象（biz_type+biz_id）只保留一条记录，
 * 取消点赞采用状态置 0（保留历史），唯一键 uk_user_biz 兜底并发重复插入。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("liked_record")
public class LikedRecord extends BaseEntity {

    /** 记录 id（自增主键） */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 点赞人 id（lms_user.user.id） */
    private Long userId;

    /** 点赞对象类型（1课程/2笔记/3问答），取值见 BizType 枚举 */
    private Integer bizType;

    /** 点赞对象 id（课程/笔记/问答的 id） */
    private Long bizId;

    /** 点赞状态：1已赞 0已取消（保留历史） */
    private Integer status;
}
