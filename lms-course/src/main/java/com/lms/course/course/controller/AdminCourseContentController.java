package com.lms.course.course.controller;

import com.lms.common.domain.R;
import com.lms.course.course.domain.dto.CatalogFormDTO;
import com.lms.course.course.domain.dto.ChapterFormDTO;
import com.lms.course.course.service.ICourseContentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 课程内容管理接口（教师端）
 *
 * 职责：维护课程目录（章节树）与章节 markdown 正文，只接收参数调用服务。
 */
@Tag(name = "课程内容管理接口（教师端）")
@RestController
@RequestMapping("/admin")
@RequiredArgsConstructor
public class AdminCourseContentController {

    private final ICourseContentService contentService;

    /** 新增目录节点（章或节） */
    @PostMapping("/courses/{courseId}/catalog")
    @Operation(summary = "新增章节（章或节）")
    public R<Long> addCatalog(@PathVariable("courseId") Long courseId,
                              @RequestBody @Valid CatalogFormDTO dto) {
        return R.ok(contentService.addCatalog(courseId, dto));
    }

    /** 修改目录节点 */
    @PutMapping("/catalog/{catalogId}")
    @Operation(summary = "修改章节")
    public R<Void> updateCatalog(@PathVariable("catalogId") Long catalogId,
                                 @RequestBody @Valid CatalogFormDTO dto) {
        contentService.updateCatalog(catalogId, dto);
        return R.ok();
    }

    /** 删除目录节点（递归删节与正文） */
    @DeleteMapping("/catalog/{catalogId}")
    @Operation(summary = "删除章节（含子节与正文）")
    public R<Void> deleteCatalog(@PathVariable("catalogId") Long catalogId) {
        contentService.deleteCatalog(catalogId);
        return R.ok();
    }

    /** 保存章节正文（不存在插入，存在覆盖） */
    @PutMapping("/catalog/{catalogId}/chapter")
    @Operation(summary = "保存章节正文（markdown）")
    public R<Void> saveChapter(@PathVariable("catalogId") Long catalogId,
                               @RequestBody @Valid ChapterFormDTO dto) {
        contentService.saveChapter(catalogId, dto);
        return R.ok();
    }
}
