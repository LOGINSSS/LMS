package com.lms.course.course.domain.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.lms.common.domain.po.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 课程分类标签实体
 *
 * 对应表 course_category：全局共享的分类库（所有教师可见），
 * 课程只存分类名称（course.category），分类下拉从本表读取并经 Redis 缓存。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("course_category")
public class CourseCategory extends BaseEntity {

    /** 分类 id（自增主键） */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 分类名称（唯一，教师下拉新增） */
    private String name;

    /** 创建人（教师 user id；默认种子数据为空） */
    private Long createBy;
}
