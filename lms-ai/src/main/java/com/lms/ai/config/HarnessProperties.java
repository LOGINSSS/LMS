package com.lms.ai.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 全局 Harness（管控护栏）配置（前缀 lms.ai.harness，spec docs/GLOBAL_HARNESS_SPEC.md）
 *
 * 收敛在会话全局、不下沉专家 Agent：
 * - invite：邀请前置拦截（深度 / 会话白名单 / 高危 HITL）
 * - hitl：人工确认（HITL）请求与批复（Redis 存储）
 * - session：会话生命周期兜底（turn / token 预算）
 * - trace：统一可观测埋点（Redis 环形缓冲 + 查询接口）
 */
@Data
@ConfigurationProperties(prefix = "lms.ai.harness")
public class HarnessProperties {

    /** 全局 Harness 总开关（false = 全部放行只保留日志，供阶段回退） */
    private boolean enabled = true;

    /** 邀请前置拦截子配置 */
    private Invite invite = new Invite();

    /** 人工确认（HITL）子配置 */
    private Hitl hitl = new Hitl();

    /** 会话生命周期兜底子配置 */
    private Session session = new Session();

    /** 可观测 Trace 子配置 */
    private Trace trace = new Trace();

    /** 邀请前置拦截 */
    @Data
    public static class Invite {

        /** 邀请最大嵌套深度（含首层调用；spec §5.3 ≤3：个人 agent → 子 agent → 工具） */
        private int maxDepth = 3;

        /**
         * 高危专家（声明 risk=high）邀请策略：
         * ask  = 强制人工确认（HITL，默认，安全底线）；
         * deny = 直接拒绝（邀请该专家不可用，让主 Agent 重规划）；
         * log  = 仅记录放行（演示/联调用，不推荐生产）。
         */
        private String highRiskPolicy = "ask";

        /** 会话级额外禁止邀请的专家名（跨角色越权名单，叠加在声明 subAgents 白名单之上） */
        private List<String> denyAgents = new ArrayList<>();

        /**
         * 会话白名单覆盖：inviter agent 名 → 允许邀请的 target 集合（可选）。
         * 不配置时 = 声明层 subAgents；配置后在该 agent 上收窄/放宽（仅影响拦截校验，不影响 toolkit 注册）。
         */
        private Map<String, List<String>> whitelistOverride = new HashMap<>();
    }

    /** 人工确认（HITL） */
    @Data
    public static class Hitl {

        /** HITL 开关（false = 高危策略退化为 deny：无人工通道时宁可拒绝） */
        private boolean enabled = true;

        /** 确认请求 Redis TTL（秒，超时未批复自动失效） */
        private long requestTtlSeconds = 600;

        /** 批复缓存 TTL（秒）：批复后同会话同目标在窗口内免重复确认 */
        private long approvalTtlSeconds = 1800;

        /** 高危邀请文本模板里的确认提示（给前端/用户看的说明） */
        private String hint = "该操作涉及业务数据写入/外部触达，需要人工确认";
    }

    /** 会话生命周期兜底 */
    @Data
    public static class Session {

        /** 会话最大用户轮次（0 = 不限；超限终止会话并提示新建） */
        private int maxUserTurns = 0;

        /** 会话最大估算 token（0 = 不限；近似 text.length()/2，超限终止会话） */
        private long maxTokens = 0;
    }

    /** 可观测 Trace */
    @Data
    public static class Trace {

        /** Trace 埋点开关 */
        private boolean enabled = true;

        /** 每会话 Redis 环形缓冲保留条数 */
        private int recentLimit = 200;

        /** Trace Redis TTL（天） */
        private int ttlDays = 7;
    }
}
