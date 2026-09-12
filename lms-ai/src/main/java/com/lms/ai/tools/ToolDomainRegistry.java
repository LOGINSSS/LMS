package com.lms.ai.tools;

import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 工具域注册表（spec HEAVY_HARNESS_SPEC §6.1：声明能力域 → 工具 Bean）
 *
 * 收敛 ToolFactory 原先散落的 prefix→Bean 映射，供两处复用：
 * - ToolFactory.build：按声明域注册工具（网关壳化后经 GuardedFunctionTool 注册）
 * - AnnotatedToolMetaService：启动扫描每个域的 @Tool 方法生成元数据（readOnly/risk）
 */
@Component
public class ToolDomainRegistry {

    private final CourseTools courseTools;
    private final GrabTools grabTools;
    private final ExamTools examTools;
    private final LearningTools learningTools;
    private final SearchTools searchTools;
    private final RemarkTools remarkTools;
    private final UserTools userTools;
    private final MediaTools mediaTools;
    private final StatisticsTools statisticsTools;
    private final KbTools kbTools;
    private final ImTools imTools;
    private final TaskTools taskTools;
    private final EvalTools evalTools;
    private final LearningPipelineTools learningPipelineTools;

    public ToolDomainRegistry(CourseTools courseTools, GrabTools grabTools, ExamTools examTools,
                              LearningTools learningTools, SearchTools searchTools,
                              RemarkTools remarkTools, UserTools userTools,
                              MediaTools mediaTools, StatisticsTools statisticsTools,
                              KbTools kbTools, ImTools imTools, TaskTools taskTools,
                              EvalTools evalTools, LearningPipelineTools learningPipelineTools) {
        this.courseTools = courseTools;
        this.grabTools = grabTools;
        this.examTools = examTools;
        this.learningTools = learningTools;
        this.searchTools = searchTools;
        this.remarkTools = remarkTools;
        this.userTools = userTools;
        this.mediaTools = mediaTools;
        this.statisticsTools = statisticsTools;
        this.kbTools = kbTools;
        this.imTools = imTools;
        this.taskTools = taskTools;
        this.evalTools = evalTools;
        this.learningPipelineTools = learningPipelineTools;
    }

    /** 域前缀 → 工具 Bean（惰性映射） */
    public Object bean(String prefix) {
        return switch (prefix) {
            case "course" -> courseTools;
            case "grab" -> grabTools;
            case "exam" -> examTools;
            case "learning" -> learningTools;
            case "search" -> searchTools;
            case "remark" -> remarkTools;
            case "user" -> userTools;
            case "media" -> mediaTools;
            case "statistics" -> statisticsTools;
            case "kb" -> kbTools;
            case "im" -> imTools;
            case "task" -> taskTools;
            case "eval" -> evalTools;
            case "pipeline" -> learningPipelineTools;
            default -> null;
        };
    }

    /** 全量域（启动扫描元数据用） */
    public Map<String, Object> domains() {
        Map<String, Object> map = new LinkedHashMap<>();
        for (String prefix : new String[]{"course", "grab", "exam", "learning", "search", "remark",
                "user", "media", "statistics", "kb", "im", "task", "eval", "pipeline"}) {
            Object b = bean(prefix);
            if (b != null) {
                map.put(prefix, b);
            }
        }
        return map;
    }
}
