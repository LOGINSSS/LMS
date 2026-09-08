package com.lms.exam.exam.controller;

import com.lms.common.domain.R;
import com.lms.exam.exam.domain.dto.ExamScheduleFormDTO;
import com.lms.exam.exam.domain.dto.PaperSubmitDTO;
import com.lms.exam.exam.domain.po.ExamSchedule;
import com.lms.exam.exam.service.IExamScheduleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 发布物排期接口（v1 收尾 P2b-③/④：作业/考试）
 *
 * 教师端（/admin/exam-schedules/**，仅老师）：发布排期（作业 bizType=2 / 考试 bizType=1，挂已发布卷面）、结束、我的排期；
 * 学生端（/exam-schedules/**，登录即可）：我的发布物（按课程过滤，可按类型过滤，日历渲染用）、详情、窗口内交卷（服务端判分）。
 */
@Tag(name = "发布物排期接口（考试/作业）")
@RestController
@RequiredArgsConstructor
public class ExamScheduleController {

    private final IExamScheduleService scheduleService;

    // ---------- 教师端 ----------

    @PostMapping("/admin/exam-schedules")
    @Operation(summary = "发布排期（老师：作业 bizType=2 / 考试 bizType=1，挂已发布卷面，可多课程）")
    public R<Long> publish(@RequestBody @Valid ExamScheduleFormDTO dto) {
        return R.ok(scheduleService.publish(dto));
    }

    @PostMapping("/admin/exam-schedules/{id}/close")
    @Operation(summary = "结束排期（发布者本人）")
    public R<Void> close(@PathVariable("id") Long id) {
        scheduleService.close(id);
        return R.ok();
    }

    @GetMapping("/admin/exam-schedules")
    @Operation(summary = "我发布的排期列表（老师）")
    public R<List<ExamSchedule>> listByTeacher() {
        return R.ok(scheduleService.listByTeacher());
    }

    // ---------- 学生端 ----------

    @GetMapping("/exam-schedules/mine")
    @Operation(summary = "我的考试/作业列表（按我的课程过滤，已发布且未截止；bizType=1 考试 2 作业，可空=全部）")
    public R<List<ExamSchedule>> listMine(@RequestParam(value = "courseIds", required = false) String courseIds,
                                          @RequestParam(value = "bizType", required = false) Integer bizType) {
        List<Long> mine = parseCourseIds(courseIds);
        return R.ok(scheduleService.listMine(mine, bizType));
    }

    @GetMapping("/exam-schedules/{id}")
    @Operation(summary = "排期详情（已发布可见，或本人发布）")
    public R<ExamSchedule> detail(@PathVariable("id") Long id) {
        return R.ok(scheduleService.detail(id));
    }

    @PostMapping("/exam-schedules/{id}/submit")
    @Operation(summary = "排期交卷（作业/考试窗口内；服务端按卷面快照确定性判分，作业允许多次重做）")
    public R<Map<String, Object>> submit(@PathVariable("id") Long id,
                                         @RequestBody @Valid PaperSubmitDTO dto) {
        return R.ok(scheduleService.submitToSchedule(id, dto));
    }

    @PostMapping("/exam-schedules/{id}/submit-async")
    @Operation(summary = "考试异步提交（前端轨：锁页交卷 → Kafka 幂等消费端判分回流），返回 submissionId")
    public R<Map<String, Object>> submitAsync(@PathVariable("id") Long id,
                                              @RequestBody @Valid PaperSubmitDTO dto) {
        return R.ok(scheduleService.submitAsync(id, dto));
    }

    // ---------- 内部 ----------

    private List<Long> parseCourseIds(String csv) {
        if (csv == null || csv.isBlank()) {
            return List.of();
        }
        return Arrays.stream(csv.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(Long::valueOf)
                .collect(Collectors.toList());
    }
}
