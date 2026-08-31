package com.lms.course.course.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 课程目录节点出参（左栏可滚动大纲）
 *
 * 使用场景：GET /courses/{id}/catalog 返回章节树，前端渲染左侧大纲；
 * 只含结构字段（不拖正文大字段），点击节点后按 catalogId 单独拉正文。
 */
@Data
@Schema(description = "课程目录节点")
public class CatalogNodeVO {

    /** 目录节点 id（chapterId，正文查询入参） */
    private Long id;

    /** 章/节名称 */
    private String name;

    /** 层级：1 章 / 2 节 */
    private Integer level;

    /** 子节点（节） */
    private List<CatalogNodeVO> children = new ArrayList<>();
}
