package com.lms.course.course.controller;

import com.lms.common.domain.R;
import com.lms.course.course.domain.vo.CatalogNodeVO;
import com.lms.course.course.domain.vo.ChapterVO;
import com.lms.course.course.service.ICourseContentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 课程内容前台接口（左栏大纲 + 右栏正文）
 *
 * 职责：学生/老师共用两栏视图数据源，只读不承载业务逻辑。
 */
@Tag(name = "课程内容前台接口")
@RestController
@RequestMapping
@RequiredArgsConstructor
public class CourseContentController {

    private final ICourseContentService contentService;

    /** 课程目录树（左栏可滚动大纲） */
    @GetMapping("/courses/{id}/catalog")
    @Operation(summary = "课程目录树（大纲）")
    public R<List<CatalogNodeVO>> getCatalog(@PathVariable("id") Long courseId) {
        return R.ok(contentService.getCatalog(courseId));
    }

    /** 章节正文（右栏 markdown 片段） */
    @GetMapping("/catalog/{catalogId}/chapter")
    @Operation(summary = "章节正文")
    public R<ChapterVO> getChapter(@PathVariable("catalogId") Long catalogId) {
        return R.ok(contentService.getChapter(catalogId));
    }
}
