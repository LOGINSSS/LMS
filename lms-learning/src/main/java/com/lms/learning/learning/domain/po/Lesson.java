package com.lms.learning.learning.domain.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.lms.common.domain.po.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 课次实体
 *
 * 对应表 lesson。业务含义：课程下的学习单元（如视频课时），教师创建，按 sort 排序。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("lesson")
public class Lesson extends BaseEntity {

    /** 课次 id（自增主键） */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属课程 id（lms_course.course.id） */
    private Long courseId;

    /** 课次名称 */
    private String name;

    /** 关联媒资视频 id（lms_media.media.id，可空） */
    private Long mediaId;

    /** 课次排序（小在前） */
    private Integer sort;
}
