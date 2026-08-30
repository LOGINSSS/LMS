package com.lms.kb.eval.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lms.common.domain.R;
import com.lms.common.domain.dto.PageDTO;
import com.lms.kb.eval.domain.RagasExportSample;
import com.lms.kb.knowledge.domain.po.RagEvalSample;
import com.lms.kb.knowledge.mapper.RagEvalSampleMapper;
import com.lms.kb.rag.domain.RagChunk;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * RAGAS 评估接口
 *
 * 链路：RAG 问答时 EvalSampleCollector 自动落库中间产物 →
 * 本接口查询/标注标准答案 → GET /eval/export 导出 ragas 数据集 →
 * 外部 Python ragas 脚本离线计算指标（见 scripts/ragas-eval/）。
 */
@Tag(name = "RAGAS 评估接口")
@RestController
@RequestMapping("/eval")
@RequiredArgsConstructor
public class EvalController {

    private final RagEvalSampleMapper sampleMapper;
    private final ObjectMapper objectMapper;

    @GetMapping("/samples")
    @Operation(summary = "分页查询评估样本")
    public R<PageDTO<RagEvalSample>> querySamples(
            @RequestParam(value = "courseId", required = false) Long courseId,
            @RequestParam(value = "pageNo", defaultValue = "1") Integer pageNo,
            @RequestParam(value = "pageSize", defaultValue = "10") Integer pageSize) {
        LambdaQueryWrapper<RagEvalSample> wrapper = new LambdaQueryWrapper<>();
        if (courseId != null) {
            wrapper.eq(RagEvalSample::getCourseId, courseId);
        }
        wrapper.orderByDesc(RagEvalSample::getId);
        Page<RagEvalSample> page = sampleMapper.selectPage(new Page<>(pageNo, pageSize), wrapper);
        return R.ok(PageDTO.of(page.getTotal(), page.getRecords()));
    }

    @PostMapping("/samples/{id}/ground-truth")
    @Operation(summary = "标注标准答案（评估 ground_truth）")
    public R<Void> labelGroundTruth(@PathVariable("id") Long id, @RequestBody Map<String, String> body) {
        RagEvalSample sample = sampleMapper.selectById(id);
        if (sample != null) {
            sample.setGroundTruth(body.get("groundTruth"));
            sampleMapper.updateById(sample);
        }
        return R.ok();
    }

    @GetMapping("/export")
    @Operation(summary = "导出 RAGAS 0.4.x 评估数据集（user_input/response/retrieved_contexts/reference）")
    public R<List<RagasExportSample>> export(@RequestParam(value = "courseId", required = false) Long courseId) {
        LambdaQueryWrapper<RagEvalSample> wrapper = new LambdaQueryWrapper<>();
        if (courseId != null) {
            wrapper.eq(RagEvalSample::getCourseId, courseId);
        }
        wrapper.orderByAsc(RagEvalSample::getId);
        List<RagEvalSample> samples = sampleMapper.selectList(wrapper);
        List<RagasExportSample> out = samples.stream().map(s -> new RagasExportSample(
                s.getQuestion(),          // user_input
                s.getAnswer(),            // response
                parseContextTexts(s.getContexts()), // retrieved_contexts
                s.getGroundTruth()        // reference
        )).toList();
        return R.ok(out);
    }

    /** contexts JSON（[{text,source,docType,metadata,score}]）→ 文本列表 */
    private List<String> parseContextTexts(String contextsJson) {
        if (contextsJson == null || contextsJson.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(contextsJson, new TypeReference<List<RagChunk>>() {
            }).stream().map(RagChunk::text).toList();
        } catch (Exception e) {
            return List.of();
        }
    }
}
