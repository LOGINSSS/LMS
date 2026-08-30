package com.lms.ai.memory;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 行为事件流水（spec §4.3：L2 的原料：对话/提问/答题/签到/搜索/点赞/文档上传）
 */
@Data
@TableName("agent_user_behavior")
public class AgentUserBehavior {

    /** 主键 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 用户 id */
    private Long userId;

    /** 事件类型：chat/ask_question/answer/quiz/sign_in/search/like/kb_upload... */
    private String eventType;

    /** 事件明细 JSON（问题主题/题目知识点/搜索词...） */
    private String payload;

    /** 创建时间 */
    private LocalDateTime createTime;
}
