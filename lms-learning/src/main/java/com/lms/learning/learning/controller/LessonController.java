package com.lms.learning.learning.controller;

import com.lms.common.domain.R;
import com.lms.learning.learning.domain.dto.LearningRecordFormDTO;
import com.lms.learning.learning.domain.dto.LessonFormDTO;
import com.lms.learning.learning.domain.vo.LessonVO;
import com.lms.learning.learning.service.ILearningService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 课次与学习接口
 *
 * 职责：课次列表/详情（学生查看）、教师建课次、学习进度上报与课程进度查询。
 */
@Tag(name = "课次与学习接口")
@RestController
@RequestMapping("/lessons")
@RequiredArgsConstructor
public class LessonController {

    private final ILearningService learningService;

    /** 按课程查课次列表 */
    @GetMapping
    @Operation(summary = "按课程查课次列表")
    public R<List<LessonVO>> listLessons(@RequestParam("courseId") Long courseId) {
        return R.ok(learningService.listLessons(courseId));
    }

    /** 课次详情 */
    @GetMapping("/{id}")
    @Operation(summary = "课次详情")
    public R<LessonVO> getLesson(@PathVariable("id") Long id) {
        return R.ok(learningService.getLesson(id));
    }

    /** 上报学习进度（首次学习发放学习积分） */
    @PostMapping("/learn/records")
    @Operation(summary = "上报学习进度")
    public R<Void> recordLearning(@RequestBody @Valid LearningRecordFormDTO dto) {
        learningService.recordLearning(dto);
        return R.ok();
    }

    /** 我的课程学习进度（已学课次占比） */
    @GetMapping("/learn/progress")
    @Operation(summary = "课程学习进度")
    public R<Integer> getProgress(@RequestParam("courseId") Long courseId) {
        return R.ok(learningService.getCourseProgress(courseId));
    }

    /** 教师新增课次 */
    @PostMapping("/admin/lessons")
    @Operation(summary = "教师新增课次")
    public R<Long> addLesson(@RequestBody @Valid LessonFormDTO dto) {
        return R.ok(learningService.addLesson(dto));
    }
}
