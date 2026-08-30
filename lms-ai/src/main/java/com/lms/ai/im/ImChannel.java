package com.lms.ai.im;

import java.util.Map;

/**
 * IM 渠道抽象（spec §7.2：agent 侧只依赖接口，平台适配器可插拔）
 *
 * 对应 AgentScope 官方 channel 层的简化自实现：接入成本更低、可控。
 */
public interface ImChannel {

    /** 推送一条消息到目标（老师小助手） */
    ImSendResult push(ImMessage msg);

    /** 平台回调解析为内部消息（IM → agent 回流），验签失败抛异常 */
    ImMessage parseCallback(Map<String, Object> payload);

    /** 平台类型：wecom / wechat / qq / console */
    String platform();
}
