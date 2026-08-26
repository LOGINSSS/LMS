package com.lms.learning.learning.controller;

import com.lms.common.domain.R;
import com.lms.common.domain.dto.PageDTO;
import com.lms.learning.learning.domain.dto.NoteFormDTO;
import com.lms.learning.learning.domain.query.NotePageQuery;
import com.lms.learning.learning.domain.vo.NoteVO;
import com.lms.learning.learning.service.ILearningService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 笔记接口
 *
 * 职责：笔记增删改查（改/删限本人）。
 */
@Tag(name = "笔记接口")
@RestController
@RequestMapping("/notes")
@RequiredArgsConstructor
public class NoteController {

    private final ILearningService learningService;

    /** 新增笔记 */
    @PostMapping
    @Operation(summary = "新增笔记")
    public R<Long> addNote(@RequestBody @Valid NoteFormDTO dto) {
        return R.ok(learningService.addNote(dto));
    }

    /** 按课程分页查笔记 */
    @GetMapping("/page")
    @Operation(summary = "按课程分页查笔记")
    public R<PageDTO<NoteVO>> listNotes(NotePageQuery query) {
        return R.ok(learningService.listNotes(query));
    }

    /** 修改自己的笔记 */
    @PutMapping("/{id}")
    @Operation(summary = "修改笔记")
    public R<Void> updateNote(@PathVariable("id") Long id, @RequestBody NoteFormDTO dto) {
        learningService.updateNote(id, dto);
        return R.ok();
    }

    /** 删除自己的笔记 */
    @DeleteMapping("/{id}")
    @Operation(summary = "删除笔记")
    public R<Void> deleteNote(@PathVariable("id") Long id) {
        learningService.deleteNote(id);
        return R.ok();
    }
}
