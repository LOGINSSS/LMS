package com.lms.kb.knowledge.constants;

import com.lms.common.constants.ErrorInfo;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 知识库服务错误码（业务错误码 2301 起，全局唯一）
 */
@Getter
@AllArgsConstructor
public enum KbErrorInfo implements ErrorInfo {

    /** 按 id 查询或操作不存在的知识库时抛出 */
    KB_NOT_FOUND(2301, "知识库不存在"),

    /** 归属（课程/用户）已存在知识库（uk(owner_type, owner_id) 唯一约束） */
    KB_ALREADY_EXISTS(2302, "该归属已创建知识库"),

    /** 知识库被禁用（status != 1）时禁止检索/上传 */
    KB_DISABLED(2303, "知识库已禁用"),

    /** 文档不存在 */
    DOC_NOT_FOUND(2304, "文档不存在"),

    /** 不支持的文件类型 */
    DOC_TYPE_UNSUPPORTED(2305, "不支持的文件类型"),

    /** 文档解析/入库失败 */
    DOC_PROCESS_FAILED(2306, "文档处理失败"),

    /** 知识库未配置 API Key（DashScope / DeepSeek） */
    AI_KEY_MISSING(2307, "AI 服务未配置（缺少 API Key）");

    private final int code;
    private final String msg;
}
