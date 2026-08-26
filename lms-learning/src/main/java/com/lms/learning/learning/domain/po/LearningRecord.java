package com.lms.learning.learning.domain.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.lms.common.domain.po.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 学习记录实体
 *
 * 对应表 learning_record。业务含义：用户对课次的学习进度，同一用户同一课次唯一
 * （uk_user_lesson），首次学习发放学习积分。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("learning_record")
public class LearningRecord extends BaseEntity {

    /** 学习记录 id（自增主键） */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 学习者（lms_user.user.id） */
    private Long userId;

    /** 课程 id */
    private Long courseId;

    /** 课次 id（关联 lesson.id） */
    private Long lessonId;

    /** 学习进度百分比（0-100） */
    private Integer progress;

    /** 最近学习时间 */
    private LocalDateTime lastLearnTime;
}
