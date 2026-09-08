package com.lms.ai.harness;

import cn.hutool.core.util.StrUtil;
import com.lms.ai.config.HarnessProperties;
import com.lms.ai.config.HarnessV2Properties;
import com.lms.ai.declaration.AgentDeclaration;
import com.lms.ai.registry.AgentRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * PolicyEngine：重型 Harness（v2）统一确定性策略判定（spec HEAVY_HARNESS_SPEC §8）
 *
 * 收敛 v1（GLOBAL_HARNESS_SPEC）守卫逻辑 + 新增动作级 HITL / 角色矩阵 / 工具判定骨架：
 *
 * 判定链（确定性顺序，任一不过即止，不依赖 prompt）：
 * 1. 总开关（v2 关 或 v1 关）→ 放行（阶段回退，只留日志）；
 * 2. 角色矩阵：ActionType × targetPattern × minUserType（身份级硬规则）；
 * 3. 目标存在性：邀请目标不在注册表 → DENY（幻觉邀请）；
 * 4. 会话白名单：deny-agents / whitelist-override / 声明 subAgents；
 * 5. 深度：invite depth ≥ max-depth → DENY（防死循环）；
 * 6. 风险分级：readOnly → ALLOW；risk=high → ask(HITL)/deny/log；
 *    - 无会话上下文 → fail-closed 拒绝；有批复缓存 → 放行/拒绝；否则创建动作级确认请求 → ASK。
 *
 * 权限模型：身份（userId/userType，RuntimeContext 透传）由本引擎前置快速拒绝 + 下游业务服务最终鉴权；
 * 能力（能调什么）由 声明白名单 ∩ 角色矩阵 ∩ 意图/会话过滤（后续阶段）决定。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PolicyEngine {

    /** 邀请上下文（GuardedAgentTool 组装） */
    public record InviteCtx(String sessionId, Long userId, Integer userType,
                            String inviterAgent, String targetAgent, int depth) {
    }

    /** 工具调用上下文（GuardedFunctionTool 组装，P1 接入） */
    public record ToolCtx(String sessionId, Long userId, Integer userType,
                          String agentName, String toolId, Map<String, Object> args,
                          String pipelineToken) {
    }

    /** 代码内置基础角色规则（追加规则走 v2 roleRules 配置） */
    private static final List<String> BASE_ROLE_RULES = List.of(
            // 高危专家（risk=high）仅老师（userType=2）可邀请 —— 声明白名单之外的第二道硬规则
            "INVITE_AGENT:exam-agent:2",
            "INVITE_AGENT:course-agent:2",
            "INVITE_AGENT:im-agent:2",
            "INVITE_AGENT:media-agent:2",
            // 题库全域（exam.*，含只读检索/取题）仅老师 —— 透题红线 §8.9 R1/R2：
            // AI 自测链路已 KB 化（无 exam 调用），学生对题库任何读取/写入一律 DENY
            "CALL_TOOL:exam.*:2");

    private final AgentRegistry registry;
    private final HarnessProperties v1;
    private final HarnessV2Properties v2;
    private final HitlService hitlService;
    private final ToolMetaService toolMetaService;
    private final PipelineTokenService pipelineTokenService;
    private final HarnessSessionGuard sessionGuard;

    // ==================== 入口：邀请（v1 GuardedAgentTool 拦截面迁移） ====================

    /** 邀请决策入口：确定性判定 + 会话邀请预算（P4：max-invites>0 时记账，超限 DENY） */
    public PolicyDecision decideInvite(InviteCtx c) {
        PolicyDecision d = decideInvite0(c);
        if (d.isAllowed() && v2.getMaxInvites() > 0 && StrUtil.isNotBlank(c.sessionId())) {
            String reason = sessionGuard.countLimited(c.sessionId(), HarnessSessionGuard.FIELD_INVITES,
                    v2.getMaxInvites());
            if (reason != null) {
                return PolicyDecision.deny("BUDGET", "Harness 拦截：会话邀请预算超限——" + reason
                        + "。请基于已有结果直接完成任务。");
            }
        }
        return d;
    }

    /** 邀请确定性判定链（无预算记账，P4 由入口包装） */
    private PolicyDecision decideInvite0(InviteCtx c) {
        if (!enabled()) {
            return PolicyDecision.allow("harness 关闭，放行（仅日志）");
        }
        String target = c.targetAgent();
        String inviter = c.inviterAgent();

        // 2. 角色矩阵（身份级硬规则）
        String roleReason = checkRoleRules(ActionType.INVITE_AGENT, target, c.userType());
        if (roleReason != null) {
            return PolicyDecision.deny("ROLE", roleReason);
        }

        // 3. 注册表存在性（幻觉邀请不存在的专家）
        if (StrUtil.isBlank(target) || !registry.contains(target)) {
            return PolicyDecision.deny("WHITELIST",
                    "Harness 拦截：目标专家 " + safe(target) + " 不在 Agent 注册表（疑似幻觉邀请）。"
                            + "请检查你的计划，只邀请注册表内、任务确实需要的专家。");
        }

        // 4. 会话白名单
        String whitelistReason = checkWhitelist(inviter, target);
        if (whitelistReason != null) {
            return PolicyDecision.deny("WHITELIST", whitelistReason);
        }

        // 5. 深度校验（防无限嵌套/死循环）
        if (c.depth() >= v1.getInvite().getMaxDepth()) {
            return PolicyDecision.deny("DEPTH",
                    "Harness 拦截：邀请链已达最大深度 " + v1.getInvite().getMaxDepth()
                            + "（当前 depth=" + c.depth() + "），疑似死循环邀请。"
                            + "请停止继续嵌套邀请，基于已有结果直接完成任务。");
        }

        // 6. 高危校验（risk=high → ask/deny/log）
        AgentDeclaration decl = registry.get(target);
        if (decl.isHighRisk()) {
            return checkHighRisk(ActionType.INVITE_AGENT, target,
                    c.sessionId(), c.userId(), inviter, decl);
        }
        return PolicyDecision.allow();
    }

    // ==================== 入口：工具调用（GuardedFunctionTool 网关） ====================

    /** 工具决策入口：确定性判定 + 写工具会话预算记账（P4：max-write-calls>0 时写工具放行前 +1，超限 DENY） */
    public PolicyDecision decideTool(ToolCtx c) {
        PolicyDecision d = decideTool0(c);
        if (d.isAllowed() && v2.getMaxWriteCalls() > 0 && StrUtil.isNotBlank(c.sessionId())
                && c.toolId() != null) {
            Optional<ToolMetaService.ToolMeta> metaOpt = toolMetaService.resolve(c.toolId());
            if (metaOpt.isPresent() && !metaOpt.get().readOnly()) {
                String reason = sessionGuard.countLimited(c.sessionId(), HarnessSessionGuard.FIELD_WRITES,
                        v2.getMaxWriteCalls());
                if (reason != null) {
                    return PolicyDecision.deny("BUDGET", "Harness 拦截：会话写操作预算超限——" + reason
                            + "。请先结束当前任务。");
                }
            }
        }
        return d;
    }

    /** 工具确定性判定链（无预算记账，P4 由入口包装） */
    private PolicyDecision decideTool0(ToolCtx c) {
        if (!enabled()) {
            return PolicyDecision.allow("harness 关闭，放行（仅日志）");
        }
        String toolId = c.toolId();

        // 0. 管道令牌豁免：服务端签发的一次性令牌（绑 sessionId+userId）→ 管道内写回流免确认
        //    （身份仍限本人：令牌绑定发起用户；P1 阶段管道写为 Java 直写，此路径为后续收紧预留）
        if (StrUtil.isNotBlank(c.pipelineToken())
                && pipelineTokenService.validateAndConsume(c.pipelineToken(), c.sessionId(), c.userId())) {
            return PolicyDecision.allow("管道令牌豁免（pipelineToken）");
        }

        // 2. 角色矩阵（身份级硬规则，如学生直调 exam.saveQuestion → DENY）
        String roleReason = checkRoleRules(ActionType.CALL_TOOL, toolId, c.userType());
        if (roleReason != null) {
            return PolicyDecision.deny("ROLE", roleReason);
        }

        // 6. 风险分级：readOnly → ALLOW；risk=high → ask/deny/log；未知元数据 → 放行（审计提示）
        Optional<ToolMetaService.ToolMeta> metaOpt = toolMetaService.resolve(toolId);
        if (metaOpt.isEmpty()) {
            return PolicyDecision.allow("工具元数据未注册（按放行处理，请检查声明域与工具名一致）");
        }
        ToolMetaService.ToolMeta meta = metaOpt.get();
        if (meta.readOnly()) {
            return PolicyDecision.allow("只读工具");
        }
        if (meta.isHighRisk()) {
            return checkHighRisk(ActionType.CALL_TOOL, toolId,
                    c.sessionId(), c.userId(), c.agentName(), null);
        }
        return PolicyDecision.allow();
    }

    // ==================== 判定链组件 ====================

    /** v1/v2 任一总开关关闭 → 放行（v1 语义：阶段回退只留日志） */
    private boolean enabled() {
        return v1.isEnabled() && v2.isEnabled();
    }

    /** 角色矩阵：命中规则且 userType < minUserType → 拒绝原因；否则 null */
    private String checkRoleRules(ActionType type, String target, Integer userType) {
        List<String> rules = new ArrayList<>(BASE_ROLE_RULES);
        if (v2.getRoleRules() != null) {
            rules.addAll(v2.getRoleRules());
        }
        int ut = userType == null ? 0 : userType;
        for (String rule : rules) {
            if (StrUtil.isBlank(rule)) {
                continue;
            }
            String[] parts = rule.trim().split(":");
            if (parts.length < 3) {
                continue;
            }
            if (!type.name().equalsIgnoreCase(parts[0].trim())) {
                continue;
            }
            String pattern = parts[1].trim();
            if (!globMatch(pattern, target)) {
                continue;
            }
            int min;
            try {
                min = Integer.parseInt(parts[2].trim());
            } catch (NumberFormatException e) {
                continue;
            }
            if (ut < min) {
                return "Harness 拦截：当前角色（userType=" + ut + "）无权执行 " + type.name()
                        + " 目标 " + target + "（需 userType≥" + min + "）。请重新规划，改用允许的能力。";
            }
        }
        return null;
    }

    /** 会话白名单校验：通过返回 null，拒绝返回原因（v1 DefaultAgentInviteGuard 逻辑迁移） */
    private String checkWhitelist(String inviter, String target) {
        List<String> deny = v1.getInvite().getDenyAgents();
        if (deny != null && deny.contains(target)) {
            return "Harness 拦截：目标专家 " + target + " 在会话禁止名单中，不允许邀请。"
                    + "请重新规划，改用允许范围内的能力完成用户请求。";
        }
        Map<String, List<String>> override = v1.getInvite().getWhitelistOverride();
        if (override != null && override.containsKey(inviter)) {
            List<String> allowed = override.get(inviter);
            if (allowed == null || !allowed.contains(target)) {
                return "Harness 拦截：当前会话上下文不允许 " + safe(inviter) + " 邀请 " + target
                        + "（会话白名单覆盖配置）。请重新规划，只使用允许的专家。";
            }
            return null;
        }
        if (StrUtil.isNotBlank(inviter) && registry.contains(inviter)) {
            AgentDeclaration inviterDecl = registry.get(inviter);
            if (inviterDecl.getSubAgents().contains(target)) {
                return null;
            }
            return "Harness 拦截：专家 " + target + " 不在 " + safe(inviter)
                    + " 的会话邀请白名单中（声明 subAgents）。可能是一次越权/幻觉邀请。"
                    + "请重新规划：只邀请你被允许调度的专家。";
        }
        return "Harness 拦截：未知邀请者 " + safe(inviter) + " 不允许向外邀请专家 " + target + "。";
    }

    /** 高危动作：按策略 ask / deny / log（动作级 HITL，spec §8.5） */
    private PolicyDecision checkHighRisk(ActionType type, String actionId,
                                         String sessionId, Long userId, String inviter,
                                         AgentDeclaration decl) {
        String policy = StrUtil.isNotBlank(v2.getHighRiskPolicy())
                ? v2.getHighRiskPolicy() : v1.getInvite().getHighRiskPolicy();
        String desc = decl == null || decl.getDescription() == null ? "" : decl.getDescription();

        if ("deny".equalsIgnoreCase(policy)) {
            return PolicyDecision.deny("HIGH_RISK",
                    "Harness 拦截：高危动作 " + type.name() + ":" + actionId
                            + "（" + desc + "），当前策略为直接拒绝。请告知用户该能力不可用，给出替代方案。");
        }
        if ("log".equalsIgnoreCase(policy)) {
            log.warn("[Harness2] 高危动作放行（策略=log，仅供联调）type={} action={} sessionId={}",
                    type, actionId, sessionId);
            return PolicyDecision.allow();
        }
        // 默认 ask：强制人工确认（动作级）
        if (!v1.getHitl().isEnabled() || StrUtil.isBlank(sessionId)) {
            return PolicyDecision.deny("HITL_UNAVAILABLE",
                    "Harness 拦截：高危动作 " + type.name() + ":" + actionId
                            + "（" + desc + "）无可用的人工确认通道（HITL 关闭或无会话上下文），已拒绝。");
        }
        if (hitlService.isApproved(sessionId, type, actionId)) {
            return PolicyDecision.allow();
        }
        if (hitlService.isRejected(sessionId, type, actionId)) {
            return PolicyDecision.deny("HIGH_RISK",
                    "Harness 拦截：高危动作 " + type.name() + ":" + actionId
                            + " 已被人工拒绝，本会话内不再询问。请告知用户该操作未获批准。");
        }
        String requestId = hitlService.create(sessionId, userId, type, actionId, inviter,
                "高危动作需人工确认：" + type.name() + ":" + actionId + "（" + desc + "）");
        if (requestId == null) {
            return PolicyDecision.deny("HITL_UNAVAILABLE",
                    "Harness 拦截：高危动作 " + type.name() + ":" + actionId + " 的确认请求创建失败，已拒绝。");
        }
        return PolicyDecision.ask("HITL_REQUIRED", requestId,
                "Harness 拦截：高危动作 " + type.name() + ":" + actionId + "（"
                        + desc + "）需要人工确认。确认请求 id=" + requestId
                        + "。请结束本轮并告知用户：需要用户批准（请求 id=" + requestId + "）后重试该操作。");
    }

    /** 简单 glob：* 匹配任意串（含空），其余按前缀匹配到 * 结束 */
    static boolean globMatch(String pattern, String target) {
        if (target == null) {
            return false;
        }
        int star = pattern.indexOf('*');
        if (star < 0) {
            return pattern.equals(target);
        }
        String prefix = pattern.substring(0, star);
        if (star == pattern.length() - 1) {
            return target.startsWith(prefix);
        }
        String suffix = pattern.substring(star + 1);
        return target.startsWith(prefix) && target.endsWith(suffix)
                && target.length() >= prefix.length() + suffix.length();
    }

    private static String safe(String s) {
        return s == null ? "<unknown>" : s;
    }
}
