package com.lms.learning.learning.domain.query;

import com.lms.common.domain.query.PageQuery;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 笔记分页查询参数
 *
 * 使用场景：按课程查看笔记列表。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "笔记分页查询参数")
public class NotePageQuery extends PageQuery {

    /** 课程 id（必传） */
    private Long courseId;
}
