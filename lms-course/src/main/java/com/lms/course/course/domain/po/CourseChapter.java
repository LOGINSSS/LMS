package com.lms.course.course.domain.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.lms.common.domain.po.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 课程章节正文实体
 *
 * 对应表 course_chapter。业务模型：一个目录节点（course_catalog.id）对应一份
 * markdown 正文（uk_catalog 一对一）；右栏详情页按 catalogId 单查本表。
 * 正文按章节粒度存 LONGTEXT，单章通常几 KB~几十 KB，MySQL 完全承载
 * （spec 0.2 §3.2：整篇 blob 不做，目录/正文分表）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("course_chapter")
public class CourseChapter extends BaseEntity {

    /** 章节正文 id（自增主键） */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 目录节点 id（course_catalog.id，uk） */
    private Long catalogId;

    /** 课程 id（冗余，按课程批量查询/统计用） */
    private Long courseId;

    /** 章节 markdown 正文 */
    private String contentMd;

    /** 字数（统计/积分参考） */
    private Integer wordCount;
}
