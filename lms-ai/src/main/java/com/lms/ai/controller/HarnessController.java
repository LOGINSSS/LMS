package com.lms.ai.controller;

import com.lms.ai.harness.HarnessSessionGuard;
import com.lms.ai.harness.HarnessTraceService;
import com.lms.ai.harness.HitlService;
import com.lms.ai.session.AgentSession;
import com.lms.ai.session.AgentSessionService;
import com.lms.common.domain.R;
import com.lms.common.exceptions.CommonException;
import com.lms.common.exceptions.UnauthorizedException;
import com.lms.common.utils.UserContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * Harness（全局管控护栏）接口（spec GLOBAL_HARNESS_SPEC §5：/agent/harness/**）
 *
 * - HITL 人工批复：高危专家邀请被拦截后，前端把确认请求 id 展示给用户，
 *   用户批准（approve）/ 拒绝（reject），批复经 Redis 生效，主 Agent 重试时放行/拒绝；
 * - Trace 查询：按会话查看邀请决策链路（谁邀请谁、决策、原因、HITL 结果），仅会话归属人；
 * - 会话用量：查看当前会话 turn/token 消耗（调试前端展示用），仅会话归属人。
 *
 * 所有接口需登录；HITL 批复仅允许请求归属用户或老师角色。
 */
@Tag(name = "Harness 管控")
@RestController
@RequestMapping("/agent/harness")
@RequiredArgsConstructor
public class HarnessController {

    private final HitlService hitlService;
    private final HarnessTraceService traceService;
    private final HarnessSessionGuard sessionGuard;
    private final AgentSessionService sessionService;

    @PostMapping("/hitl/{requestId}/approve")
    @Operation(summary = "人工批准高危操作确认请求（requestId 来自 Agent 被拦截后的提示）")
    public R<Void> approve(@PathVariable("requestId") String requestId) {
        checkHitlOwner(requestId);
        if (!hitlService.approve(requestId)) {
            throw new CommonException("确认请求不存在或已过期");
        }
        traceService.record(String.valueOf(hitlService.detail(requestId).getOrDefault("sessionId", "")),
                HarnessTraceService.EVENT_HITL_APPROVE, Map.of("requestId", requestId));
        return R.ok();
    }

    @PostMapping("/hitl/{requestId}/reject")
    @Operation(summary = "人工拒绝高危操作确认请求")
    public R<Void> reject(@PathVariable("requestId") String requestId) {
        checkHitlOwner(requestId);
        if (!hitlService.reject(requestId)) {
            throw new CommonException("确认请求不存在或已过期");
        }
        traceService.record(String.valueOf(hitlService.detail(requestId).getOrDefault("sessionId", "")),
                HarnessTraceService.EVENT_HITL_REJECT, Map.of("requestId", requestId));
        return R.ok();
    }

    @GetMapping("/hitl/{requestId}")
    @Operation(summary = "确认请求详情（前端展示：邀请谁/原因/状态）")
    public R<Map<Object, Object>> hitlDetail(@PathVariable("requestId") String requestId) {
        checkHitlOwner(requestId);
        Map<Object, Object> d = hitlService.detail(requestId);
        if (d.isEmpty()) {
            throw new CommonException("确认请求不存在或已过期");
        }
        return R.ok(d);
    }

    @GetMapping("/trace")
    @Operation(summary = "会话 Harness Trace（最近 N 条邀请决策/事件，仅会话归属人）")
    public R<List<Map<String, Object>>> trace(@RequestParam("sessionId") Long sessionId,
                                              @RequestParam(value = "limit", required = false) Integer limit) {
        requireOwned(sessionId);
        return R.ok(traceService.recent(String.valueOf(sessionId), limit == null ? 0 : limit));
    }

    @GetMapping("/usage")
    @Operation(summary = "会话用量（turns/tokens 当前消耗，仅会话归属人）")
    public R<Map<Object, Object>> usage(@RequestParam("sessionId") Long sessionId) {
        requireOwned(sessionId);
        return R.ok(sessionGuard.usage(sessionId));
    }

    // ---------- 校验 ----------

    private Long requireUser() {
        Long userId = UserContext.getUser();
        if (userId == null) {
            throw new UnauthorizedException("未登录");
        }
        return userId;
    }

    /** Trace/用量读取需校验会话归属 */
    private void requireOwned(Long sessionId) {
        AgentSession s = sessionService.getOwned(requireUser(), sessionId);
        if (s == null) {
            throw new CommonException("会话不存在或无权访问");
        }
    }

    private void checkHitlOwner(String requestId) {
        Long userId = requireUser();
        Map<Object, Object> d = hitlService.detail(requestId);
        if (d.isEmpty()) {
            throw new CommonException("确认请求不存在或已过期");
        }
        String owner = String.valueOf(d.getOrDefault("userId", ""));
        Integer userType = UserContext.getUserType();
        boolean isOwner = owner.equals(String.valueOf(userId));
        boolean isTeacher = userType != null && userType == 2;
        if (!isOwner && !isTeacher) {
            throw new CommonException("无权操作他人会话的确认请求");
        }
    }
}
