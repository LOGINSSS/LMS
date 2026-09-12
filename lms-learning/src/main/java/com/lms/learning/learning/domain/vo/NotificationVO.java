package com.lms.learning.learning.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 站内消息出参（信箱列表行）
 */
@Data
@Schema(description = "站内消息信息")
public class NotificationVO {

    /** 消息 id */
    private Long id;

    /** 消息类型：1=教师待回答 / 2=学生收到回答 / 3=教师已处理 */
    private Integer type;

    /** 关联课程 id（跳转锚点用） */
    private Long courseId;

    /** 关联问答问题 id（就地答疑用） */
    private Long questionId;

    /** 行内标题 */
    private String title;

    /** 补充内容 */
    private String content;

    /** 是否已读：0 未读 / 1 已读 */
    private Integer isRead;

    /** 创建时间 */
    private LocalDateTime createTime;
}
