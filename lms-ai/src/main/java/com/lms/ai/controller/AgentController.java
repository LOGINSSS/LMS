package com.lms.ai.controller;

import com.lms.ai.chat.AgentChatService;
import com.lms.ai.memory.AgentUserProfile;
import com.lms.ai.memory.ProfileService;
import com.lms.ai.orchestration.OrchestratorService;
import com.lms.ai.session.AgentSession;
import com.lms.ai.session.AgentSessionService;
import com.lms.common.domain.R;
import com.lms.common.utils.UserContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 个人 Agent 接口（spec §8.1：网关 /agent/** 路由）
 *
 * 所有接口需登录（网关 JWT 校验 + user-info 头 → UserContext）。
 */
@Tag(name = "个人 Agent")
@RestController
@RequestMapping("/agent")
@RequiredArgsConstructor
public class AgentController {

    private final AgentChatService chatService;
    private final AgentSessionService sessionService;
    private final ProfileService profileService;
    private final OrchestratorService orchestratorService;

    @PostMapping("/chat")
    @Operation(summary = "个人 agent 多轮对话（sessionId 缺省自动建会话）")
    public R<ChatReplyVO> chat(@RequestBody @Valid ChatRequest req) {
        AgentChatService.ChatResult r = chatService.chat(
                requireUser(), requireUserType(), req.sessionId(), req.text(), req.agentType());
        return R.ok(new ChatReplyVO(r.sessionId(), r.reply(), r.taskId()));
    }

    @PostMapping("/sessions")
    @Operation(summary = "建会话")
    public R<SessionVO> createSession(@RequestBody(required = false) CreateSessionRequest req) {
        String agentType = req == null || req.agentType() == null ? "student-agent" : req.agentType();
        AgentSession s = sessionService.createSession(requireUser(), agentType,
                req == null ? null : req.title());
        return R.ok(SessionVO.of(s));
    }

    @GetMapping("/sessions/mine")
    @Operation(summary = "我的会话列表")
    public R<List<SessionVO>> mySessions() {
        return R.ok(sessionService.listMine(requireUser()).stream().map(SessionVO::of).toList());
    }

    @DeleteMapping("/sessions/{id}")
    @Operation(summary = "结束会话（触发 L1 摘要 → L2 行为流水）")
    public R<Void> closeSession(@PathVariable("id") Long id) {
        sessionService.closeSession(requireUser(), id);
        return R.ok();
    }

    @GetMapping("/profile/mine")
    @Operation(summary = "我的画像（L2，可编辑「记住我」字段）")
    public R<AgentUserProfile> myProfile() {
        return R.ok(profileService.getOrCreate(requireUser(), requireUserType(), null));
    }

    @PostMapping("/profile/mine")
    @Operation(summary = "编辑画像（显式「记住我」，interests/learningHabits/summary）")
    public R<Void> saveProfile(@RequestBody Map<String, Object> fields) {
        profileService.saveProfile(requireUser(), fields);
        return R.ok();
    }

    @PostMapping("/ask-teacher")
    @Operation(summary = "学生快捷入口：问题 → student-agent → teacher-agent（含 IM/定时任务编排）")
    public R<ChatReplyVO> askTeacher(@RequestBody @Valid AskTeacherRequest req) {
        AgentChatService.ChatResult r = orchestratorService.askTeacher(requireUser(), req.question(), req.sessionId());
        return R.ok(new ChatReplyVO(r.sessionId(), r.reply(), r.taskId()));
    }

    @PostMapping("/teacher/generate-exam")
    @Operation(summary = "老师快捷入口：调度 exam-agent 生成考试大纲/题目")
    public R<ChatReplyVO> generateExam(@RequestBody @Valid GenerateRequest req) {
        AgentChatService.ChatResult r = orchestratorService.generateExam(requireUser(), req.requirement(), req.sessionId());
        return R.ok(new ChatReplyVO(r.sessionId(), r.reply(), r.taskId()));
    }

    @PostMapping("/teacher/generate-course")
    @Operation(summary = "老师快捷入口：调度 course-agent 建课 + 大纲 + 章节 HTML")
    public R<ChatReplyVO> generateCourse(@RequestBody @Valid GenerateRequest req) {
        AgentChatService.ChatResult r = orchestratorService.generateCourse(requireUser(), req.requirement(), req.sessionId());
        return R.ok(new ChatReplyVO(r.sessionId(), r.reply(), r.taskId()));
    }

    // ---------- 入参/出参 ----------

    public record ChatRequest(Long sessionId,
                              @NotBlank(message = "请输入内容") String text,
                              String agentType) {
    }

    public record CreateSessionRequest(String agentType, String title) {
    }

    public record AskTeacherRequest(Long sessionId, @NotBlank(message = "请输入问题") String question) {
    }

    public record GenerateRequest(Long sessionId, @NotBlank(message = "请输入需求") String requirement) {
    }

    public record ChatReplyVO(Long sessionId, String reply, String taskId) {
    }

    public record SessionVO(Long id, String agentType, String title, Integer status, Integer messageCount) {
        public static SessionVO of(AgentSession s) {
            return new SessionVO(s.getId(), s.getAgentType(), s.getTitle(), s.getStatus(), s.getMessageCount());
        }
    }

    private Long requireUser() {
        Long userId = UserContext.getUser();
        if (userId == null) {
            throw new com.lms.common.exceptions.UnauthorizedException("未登录");
        }
        return userId;
    }

    private Integer requireUserType() {
        Integer t = UserContext.getUserType();
        return t == null ? 1 : t;
    }
}
