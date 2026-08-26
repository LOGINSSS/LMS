package com.lms.learning.learning.domain.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.lms.common.domain.po.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 笔记实体
 *
 * 对应表 note。业务含义：用户在学习过程中记录的心得笔记，挂在课程/课次下，本人可改删。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("note")
public class Note extends BaseEntity {

    /** 笔记 id（自增主键） */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 笔记作者（lms_user.user.id） */
    private Long userId;

    /** 课程 id */
    private Long courseId;

    /** 课次 id（可空，课程级笔记） */
    private Long lessonId;

    /** 笔记内容 */
    private String content;
}
