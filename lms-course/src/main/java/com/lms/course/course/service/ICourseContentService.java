package com.lms.course.course.service;

import com.lms.course.course.domain.dto.CatalogFormDTO;
import com.lms.course.course.domain.dto.ChapterFormDTO;
import com.lms.course.course.domain.vo.CatalogNodeVO;
import com.lms.course.course.domain.vo.ChapterVO;

import java.util.List;

/**
 * 课程内容服务（目录 + 章节正文）
 *
 * 职责：课程内容的结构（章节树）与正文（markdown）管理，
 * 左栏大纲 / 右栏正文的两栏视图数据源（spec 0.2 §3.1~3.5）。
 */
public interface ICourseContentService {

    /**
     * 查询课程目录树（左栏大纲，轻量不含正文）
     */
    List<CatalogNodeVO> getCatalog(Long courseId);

    /**
     * 查询章节正文（右栏，按目录节点 id）
     */
    ChapterVO getChapter(Long catalogId);

    /**
     * 教师新增目录节点（章或节）
     */
    Long addCatalog(Long courseId, CatalogFormDTO dto);

    /**
     * 教师修改目录节点
     */
    void updateCatalog(Long catalogId, CatalogFormDTO dto);

    /**
     * 教师删除目录节点（含其下节与正文，递归）
     */
    void deleteCatalog(Long catalogId);

    /**
     * 教师保存章节正文（不存在则插入，存在则覆盖更新）
     */
    void saveChapter(Long catalogId, ChapterFormDTO dto);
}
