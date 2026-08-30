package com.lms.ai.im;

/**
 * IM 内部消息（spec §7.2：agent 侧统一抽象，渠道适配器可插拔）
 *
 * @param to        目标（老师/机器人标识）
 * @param text      文本内容
 * @param taskId    关联 agent 任务（回流时定位会话）
 * @param sessionId 关联 agent 会话
 * @param userId    关联用户
 */
public record ImMessage(
        String to,
        String text,
        String taskId,
        String sessionId,
        Long userId
) {

    /** 快捷构造（无任务/会话上下文） */
    public static ImMessage of(String to, String text) {
        return new ImMessage(to, text, null, null, null);
    }
}
