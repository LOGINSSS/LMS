package com.lms.ai.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * IM 管道配置（前缀 lms.ai.im，spec §7.2：渠道可插拔）
 */
@Data
@ConfigurationProperties(prefix = "lms.ai.im")
public class ImProperties {

    /** IM 管道开关（阶段 4；关闭时 push 走 Console 兜底） */
    private boolean enabled = true;

    /** 启用渠道：console | wecom | wechat | qq（练手默认 console，spec §7.2） */
    private String channel = "console";

    /** 企微群机器人 webhook（启用 wecom 时配置） */
    private String wecomWebhook = "";

    /** 平台回调验签密钥（回调接口必须验签，spec §7.3 安全） */
    private String callbackSecret = "";

    /** 老师小助手标识（推送目标，按渠道解释） */
    private String teacherTarget = "teacher-assistant";
}
