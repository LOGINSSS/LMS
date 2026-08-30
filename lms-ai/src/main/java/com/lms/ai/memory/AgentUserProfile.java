package com.lms.ai.memory;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.lms.common.domain.po.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 用户画像主表（spec §4.3：agent_user_profile，一人一行，agent 眼中的用户）
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("agent_user_profile")
public class AgentUserProfile extends BaseEntity {

    /** 主键 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** lms-auth 用户 id（唯一） */
    private Long userId;

    /** 角色：1 学生 / 2 老师 */
    private Integer role;

    /** 昵称 */
    private String displayName;

    /** 画像摘要：agent 定期压缩生成（习惯/偏好/近期目标） */
    private String summary;

    /** 兴趣标签（可同步 lms-search user_interests） */
    private String interests;

    /** 学习习惯 JSON：活跃时段/学习节奏/擅长薄弱知识点 */
    private String learningHabits;
}
