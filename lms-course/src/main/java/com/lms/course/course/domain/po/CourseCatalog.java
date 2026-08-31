package com.lms.course.course.domain.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.lms.common.domain.po.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 课程目录节点实体
 *
 * 对应表 course_catalog。业务模型：课程内容的结构骨架（章/节两级树），
 * 左栏可滚动大纲即按 course_id 查询本表（轻量、不拖正文大字段），
 * 每章正文一对一挂在 course_chapter（uk_catalog）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("course_catalog")
public class CourseCatalog extends BaseEntity {

    /** 目录节点 id（自增主键） */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 课程 id（lms_course.course.id） */
    private Long courseId;

    /** 父节点 id（0=顶级章） */
    private Long parentId;

    /** 章/节名称 */
    private String name;

    /** 排序（小在前） */
    private Integer sort;

    /** 层级：1 章 / 2 节 */
    private Integer level;
}
