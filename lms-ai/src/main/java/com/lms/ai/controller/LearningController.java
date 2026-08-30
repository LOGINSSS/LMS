package com.lms.ai.controller;

import com.lms.ai.orchestration.LearningPipelineService;
import com.lms.common.domain.R;
import com.lms.common.utils.UserContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 学习评测入口（评测业务线，需求文档 §2/§4）
 *
 * 固定管道：诊断(①) → 课程规划(②) → 习题推送(③) → 效果测评(④)，节点内 Agent 增强；
 * 中间产物留存 agent_task（§6.4 可观测性），测评结果写回学习数据中心（闭环）。
 */
@Tag(name = "学习评测（个性化学习）")
@RestController
@RequestMapping("/agent/learning")
@RequiredArgsConstructor
public class LearningController {

    private final LearningPipelineService pipeline;

    @PostMapping("/diagnose")
    @Operation(summary = "节点① 学情诊断：读取学习数据中心，输出学情报告（薄弱知识点）")
    public R<String> diagnose(@RequestBody @Valid CourseRequest req) {
        return R.ok(pipeline.diagnose(requireUser(), req.courseId()));
    }

    @PostMapping("/plan")
    @Operation(summary = "节点② 课程规划：基于学情报告生成个性化学习路径（report 缺省则先诊断）")
    public R<String> plan(@RequestBody @Valid PlanRequest req) {
        Long userId = requireUser();
        String report = req.report() == null || req.report().isBlank()
                ? pipeline.diagnose(userId, req.courseId())
                : req.report();
        return R.ok(pipeline.plan(userId, req.courseId(), report));
    }

    @PostMapping("/exercise")
    @Operation(summary = "节点③ 习题推送：基于学习路径从题库挑选适配题目（path 缺省则先诊断+规划）")
    public R<String> exercise(@RequestBody @Valid ExerciseRequest req) {
        Long userId = requireUser();
        String path = req.path() == null || req.path().isBlank()
                ? pipeline.plan(userId, req.courseId(), pipeline.diagnose(userId, req.courseId()))
                : req.path();
        return R.ok(pipeline.exercise(userId, req.courseId(), path, req.count()));
    }

    @PostMapping("/assess")
    @Operation(summary = "节点④ 效果测评：批改作答、输出评估报告，错题/成绩写回学习数据中心")
    public R<String> assess(@RequestBody @Valid AssessRequest req) {
        return R.ok(pipeline.assess(requireUser(), req.courseId(), req.answers()));
    }

    @PostMapping("/evaluate")
    @Operation(summary = "全链一次：诊断→规划→习题（测评需学生答题后单独触发）")
    public R<LearningPipelineService.EvaluateResult> evaluate(@RequestBody @Valid EvaluateRequest req) {
        return R.ok(pipeline.evaluate(requireUser(), req.courseId(), req.count()));
    }

    // ---------- 入参 ----------

    public record CourseRequest(@NotNull(message = "课程 id 不能为空") Long courseId) {
    }

    public record PlanRequest(@NotNull(message = "课程 id 不能为空") Long courseId, String report) {
    }

    public record ExerciseRequest(@NotNull(message = "课程 id 不能为空") Long courseId, String path, Integer count) {
    }

    public record AssessRequest(@NotNull(message = "课程 id 不能为空") Long courseId,
                                @NotEmpty(message = "答题结果不能为空") List<Map<String, Object>> answers) {
    }

    public record EvaluateRequest(@NotNull(message = "课程 id 不能为空") Long courseId, Integer count) {
    }

    private Long requireUser() {
        Long userId = UserContext.getUser();
        if (userId == null) {
            throw new com.lms.common.exceptions.UnauthorizedException("未登录");
        }
        return userId;
    }
}
