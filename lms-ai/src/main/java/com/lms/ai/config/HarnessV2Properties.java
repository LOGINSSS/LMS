package com.lms.ai.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 重型 Harness（v2）配置（前缀 lms.ai.harness-v2，spec docs/HEAVY_HARNESS_SPEC.md）
 *
 * 在 v1（lms.ai.harness，GLOBAL_HARNESS_SPEC 方向 A）之上叠加：
 * - enabled=false → 全部回退 v1 行为（阶段回退开关）
 * - roleRules：角色矩阵追加规则，格式 ACTION:targetPattern:minUserType
 *   ACTION ∈ INVITE_AGENT / CALL_TOOL / SCHEDULE_TASK；targetPattern 支持 * 通配（如 CALL_TOOL:exam.*:2）；
 *   命中且 userType < minUserType → 确定性 DENY（不依赖 prompt）
 * - highRiskPolicy：高危动作策略覆盖（ask/deny/log），空 = 沿用 v1 lms.ai.harness.invite.high-risk-policy
 * - toolGateway：工具执行网关开关（false = 直注 Bean 不回退包壳，仅当 v2 整体关闭时回退 v1）
 * - toolRisk：工具风险等级覆盖表 toolId(domain.method) → high（缺省 normal，按 @Tool 注解 readOnly 分流）
 * - toolSwitchOff：静态关停工具名单（动态关停走 Redis 键 tool:switch:{toolId}，ToolControlService）
 */
@Data
@ConfigurationProperties(prefix = "lms.ai.harness-v2")
public class HarnessV2Properties {

    /** 总开关（false = 回退 v1 行为：v1 也关则全放行只留日志） */
    private boolean enabled = true;

    /** 高危动作策略覆盖：ask / deny / log；空字符串 = 沿用 v1 配置 */
    private String highRiskPolicy = "";

    /** 角色矩阵追加规则（叠加在 PolicyEngine 代码内置基础规则之上） */
    private List<String> roleRules = new ArrayList<>();

    /** L3 工具执行网关：工具注册包 GuardedFunctionTool 走 PolicyEngine（false = 直注 Bean，P0 行为） */
    private boolean toolGatewayEnabled = true;

    /** 工具风险等级覆盖表：toolId(domain.method) → high（如 im.pushMessage: high）；缺省 normal */
    private Map<String, String> toolRisk = new HashMap<>();

    /** 静态关停工具名单（toolId）；动态关停/灰度走 Redis 键 tool:switch:{toolId} */
    private List<String> toolSwitchOff = new ArrayList<>();

    /** P4 会话预算：写工具调用次数上限（0=不限；超限 DENY 提示新建会话） */
    private long maxWriteCalls = 0;

    /** P4 会话预算：邀请次数上限（0=不限；超限 DENY 提示新建会话） */
    private long maxInvites = 0;

    /** P2 LLM 意图分类开关（规则层未命中时用 qwen-turbo 分类；默认关避免每轮额外调用） */
    private boolean intentLlmEnabled = false;

    /** P2 LLM 意图分类置信阈值（低于则按 UNKNOWN 自由 ReAct 兜底） */
    private double intentMinConfidence = 0.6;

    /** P2 LLM 意图分类输入长度上限（超过直接 UNKNOWN，控成本） */
    private int intentLlmMaxTextLen = 200;

    /** P4 LLM 摘要压缩开关（长会话把最旧一段压成摘要注入；false = 现状 trimHalf 裁剪） */
    private boolean summaryEnabled = false;

    /** P4 触发压缩的消息条数阈值 */
    private int summaryThreshold = 30;

    /** P4 压缩后保留的最近消息条数 */
    private int summaryKeep = 15;
}
