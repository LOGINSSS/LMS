package com.lms.learning.learning.domain.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.lms.common.domain.po.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 站内消息（信箱）实体
 *
 * 对应表 notification（lms_learning）。答疑通知流：
 * - 学生提问 → 给课程归属教师写一条「待回答」（type=1）；
 * - 教师回答 → 原教师通知转为「已处理」（type=3），并给提问学生写一条「已被老师回答」（type=2）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("notification")
public class Notification extends BaseEntity {

    /** 主键（自增） */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 接收人（lms_user.user.id） */
    private Long userId;

    /** 消息类型：1=教师待回答 / 2=学生收到回答 / 3=教师已处理 */
    private Integer type;

    /** 关联课程 id */
    private Long courseId;

    /** 关联问答问题 id */
    private Long questionId;

    /** 行内标题（如问题标题） */
    private String title;

    /** 补充内容（如问题详情 / 回答预览） */
    private String content;

    /** 是否已读：0 未读 / 1 已读 */
    private Integer isRead;
}
