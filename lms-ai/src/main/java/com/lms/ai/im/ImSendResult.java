package com.lms.ai.im;

/**
 * IM 推送结果（spec §7.2）
 *
 * @param success 是否成功
 * @param message 结果描述（失败原因 / 平台消息 id）
 */
public record ImSendResult(boolean success, String message) {

    public static ImSendResult ok(String message) {
        return new ImSendResult(true, message);
    }

    public static ImSendResult fail(String message) {
        return new ImSendResult(false, message);
    }
}
