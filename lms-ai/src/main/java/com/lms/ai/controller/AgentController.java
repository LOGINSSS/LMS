package com.lms.ai.controller;

import com.lms.ai.chat.AgentChatService;
import com.lms.ai.chat.AgentStreamService;
import com.lms.ai.application.memory.PersonalWikiService;
import com.lms.ai.application.memory.SessionClosureService;
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
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.MediaType;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

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
    private final AgentStreamService streamService;
    private final PersonalWikiService personalWiki;
    private final SessionClosureService sessionClosureService;

    @PostMapping("/chat")
    @Operation(summary = "个人 agent 多轮对话（sessionId 缺省自动建会话）")
    public R<ChatReplyVO> chat(@RequestBody @Valid ChatRequest req) {
        Integer userType = requireUserType();
        String agentType = requireAgentType(req.agentType(), userType);
        AgentChatService.ChatResult r = chatService.chat(
                requireUser(), userType, req.sessionId(), req.text(), agentType);
        return R.ok(new ChatReplyVO(r.sessionId(), r.reply(), r.taskId()));
    }

    @PostMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "Copilot 流式对话（SSE：stage/meta/delta/hitl/done/error）")
    public SseEmitter stream(@RequestBody @Valid ChatRequest req) {
        Long userId = requireUser();
        Integer userType = requireUserType();
        String agentType = requireAgentType(req.agentType(), userType);
        return streamService.stream(userId, userType, req.sessionId(), req.text(), agentType);
    }

    @PostMapping("/sessions")
    @Operation(summary = "建会话")
    public R<SessionVO> createSession(@RequestBody(required = false) CreateSessionRequest req) {
        String requestedType = req == null ? null : req.agentType();
        String agentType = requireAgentType(requestedType, requireUserType());
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
    @Operation(summary = "结束会话（异步触发 ReMe Auto Memory）")
    public R<Void> closeSession(@PathVariable("id") Long id) {
        sessionClosureService.close(requireUser(), id);
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

    @PostMapping("/memory/wiki")
    @Operation(summary = "写入我的个人 Wiki（路径由服务端生成）")
    public R<WikiMemoryVO> remember(@RequestBody @Valid RememberRequest req) {
        var ref = personalWiki.remember(requireUser(), req.title(), req.content(), req.description());
        return R.ok(new WikiMemoryVO(ref.memoryId()));
    }

    @PatchMapping("/memory/wiki/{memoryId}")
    @Operation(summary = "更正我的个人 Wiki 中一段原文")
    public R<Void> correctMemory(@PathVariable String memoryId, @RequestBody @Valid CorrectMemoryRequest req) {
        personalWiki.correct(requireUser(), memoryId, req.oldText(), req.newText());
        return R.ok();
    }

    @DeleteMapping("/memory/wiki/{memoryId}")
    @Operation(summary = "删除我的一条个人 Wiki 记忆")
    public R<Void> forget(@PathVariable String memoryId) {
        personalWiki.forget(requireUser(), memoryId);
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
        requireTeacher();
        AgentChatService.ChatResult r = orchestratorService.generateExam(requireUser(), req.requirement(), req.sessionId());
        return R.ok(new ChatReplyVO(r.sessionId(), r.reply(), r.taskId()));
    }

    @PostMapping("/teacher/generate-course")
    @Operation(summary = "老师快捷入口：调度 course-agent 建课 + 大纲 + 章节 HTML")
    public R<ChatReplyVO> generateCourse(@RequestBody @Valid GenerateRequest req) {
        requireTeacher();
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

    public record RememberRequest(
            @NotBlank(message = "请输入记忆标题") @Size(max = 100) String title,
            @NotBlank(message = "请输入记忆正文") @Size(max = 20000) String content,
            @Size(max = 500) String description) {
    }

    public record CorrectMemoryRequest(
            @NotBlank(message = "请输入待更正文本") @Size(max = 5000) String oldText,
            @NotBlank(message = "请输入更正后文本") @Size(max = 5000) String newText) {
    }

    public record WikiMemoryVO(String memoryId) {
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

    private void requireTeacher() {
        if (!Integer.valueOf(2).equals(UserContext.getUserType())) {
            throw new com.lms.common.exceptions.ForbiddenException("仅教师可使用该能力");
        }
    }

    /** 客户端只能选择与 JWT 角色一致的个人 Agent；同时兼容旧前端的 student/teacher 短名。 */
    private String requireAgentType(String requested, Integer userType) {
        String expected = Integer.valueOf(2).equals(userType) ? "teacher-agent" : "student-agent";
        if (requested == null || requested.isBlank()) {
            return expected;
        }
        String canonical = requested.endsWith("-agent") ? requested : requested + "-agent";
        if (!expected.equals(canonical)) {
            throw new com.lms.common.exceptions.ForbiddenException("无权使用该角色的个人 Agent");
        }
        return canonical;
    }
}
