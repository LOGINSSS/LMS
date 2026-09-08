package com.lms.ai.intent;

/**
 * 学习域意图枚举（spec HEAVY_HARNESS_SPEC §4.3：教育域初始集合，可扩）
 *
 * 用途：L0 IntentRouter 输出（确定性规则层 → LLM 分类层），供 prompt 提示、能力面预收窄、
 * flow 标记（透题红线按流收窄：AI 自测流禁 exam.*）与意图 Trace 度量复用。
 */
public enum Intent {

    /** 兜底：无法识别 → 行为 = 自由 ReAct（只记 Trace） */
    UNKNOWN,

    /** 闲聊 / 角色问答（不调工具） */
    DIRECT_CHAT,

    /** 我学得怎么样 / 进度 / 统计（learning 读面） */
    QUERY_LEARNING,

    /** 笔记 / 签到 / 问答 / 积分（learning 写面，risk 分级） */
    NOTE_SIGN_QA,

    /** 帮我诊断 / 规划学习路径（learning 管道 ①②） */
    DIAGNOSE_PLAN,

    /** 练习 / 自测 / 交卷测评（learning 管道 ③④，仅知识库出题，透题红线） */
    EXERCISE_ASSESS,

    /** 出题 / 考试大纲 / 试卷（老师 → exam-agent，题库面，老师身份） */
    GENERATE_EXAM,

    /** 建课 / 课程大纲 / 章节（老师 → course-agent） */
    GENERATE_COURSE,

    /** 向老师提问（student → teacher-agent，含 IM/定时编排） */
    ASK_TEACHER,

    /** 推消息给老师小助手（teacher 面 im.pushMessage，对外触达） */
    TEACHER_IM,

    /** 知识库问答 / 讲义内容咨询（kb RAG） */
    KB_RAG,

    /** 定时任务管理（task 面） */
    TASK_MANAGE,

    /** 上传 / 删除文件（media 面） */
    MEDIA_FILE;

    /** 是否可识别（非兜底） */
    public boolean isKnown() {
        return this != UNKNOWN;
    }
}
