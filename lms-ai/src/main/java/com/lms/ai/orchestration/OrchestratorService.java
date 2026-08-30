package com.lms.ai.orchestration;

import com.lms.ai.chat.AgentChatService;
import com.lms.ai.config.TaskProperties;
import com.lms.ai.task.TaskService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

/**
 * 编排服务（spec §8.1 快捷入口 / §5.2 场景 A/B/C/D）
 *
 * - askTeacher：学生问题 → student-agent → teacher-agent（含 IM / 定时任务编排，场景 C）
 * - generateExam：老师一句话 → teacher-agent → exam-agent 出大纲/题目落库（场景 A）
 * - generateCourse：老师一句话 → teacher-agent → course-agent 建课 + 大纲 + 章节 HTML（场景 B）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrchestratorService {

    private final AgentChatService chatService;
    private final TaskService taskService;
    private final TaskProperties taskProperties;

    /**
     * 学生快捷入口（spec §8.1 POST /agent/ask-teacher）：
     * student-agent 邀请 teacher-agent；同时发布 qa_remind 定时任务（2h 未回复提醒老师，spec §6.2 首个场景）
     */
    public AgentChatService.ChatResult askTeacher(Long studentId, String question, Long sessionId) {
        AgentChatService.ChatResult result = chatService.chat(studentId, 1, sessionId, "帮我问老师：" + question, "student-agent");
        // 定时任务：老师 N 小时未回复 → IM 提醒（spec §7.3【定时】）
        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("question", question);
            payload.put("studentId", studentId);
            payload.put("sessionId", result.sessionId());
            payload.put("teacherTarget", "teacher-assistant");
            taskService.schedule(TaskService.TYPE_QA_REMIND, studentId, "student-agent",
                    com.lms.ai.task.AgentTask.TRIGGER_DELAY, payload,
                    (int) taskProperties.getQaRemindDelaySeconds(), null);
        } catch (Exception e) {
            log.warn("qa_remind 任务发布失败: {}", e.getMessage());
        }
        return result;
    }

    /** 老师快捷入口：调度 exam-agent 生成考试大纲/题目（场景 A） */
    public AgentChatService.ChatResult generateExam(Long teacherId, String requirement, Long sessionId) {
        String prompt = "请调度 exam-agent 完成考试生成任务：" + requirement
                + "。流程：先生成考试大纲给老师确认，再按大纲逐题生成并调用题库工具落库、绑定业务。";
        return chatService.chat(teacherId, 2, sessionId, prompt, "teacher-agent");
    }

    /** 老师快捷入口：调度 course-agent 建课 + 大纲 + 章节 HTML（场景 B） */
    public AgentChatService.ChatResult generateCourse(Long teacherId, String requirement, Long sessionId) {
        String prompt = "请调度 course-agent 完成课程生成任务：" + requirement
                + "。流程：创建课程壳（下架态）→ 生成课程大纲（章节树）→ 逐章生成 HTML 讲义（可上传媒资/知识库）→ 汇总给老师确认后上架。";
        return chatService.chat(teacherId, 2, sessionId, prompt, "teacher-agent");
    }
}
