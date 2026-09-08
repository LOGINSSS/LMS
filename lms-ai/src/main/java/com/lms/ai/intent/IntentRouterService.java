package com.lms.ai.intent;

import cn.hutool.core.util.StrUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 意图路由服务（spec HEAVY_HARNESS_SPEC §4.2：两级识别）
 *
 * ① 确定性规则层（关键词，零成本、可预期）；
 * ② 规则未命中 → LLM 分类层（qwen-turbo，开关 intent-llm-enabled）；
 * 识别失败返回 UNKNOWN → 上层走"自由 ReAct"兜底，只记 Trace，不影响可用性。
 */
@Service
@RequiredArgsConstructor
public class IntentRouterService {

    private final IntentClassifierService classifier;

    private static final String[] ASK_TEACHER_KW = {"帮我问老师", "替我问问老师", "帮我请教老师", "转告老师"};
    private static final String[] GENERATE_EXAM_KW = {"出题", "出卷", "试卷", "考试大纲", "期中", "期末", "生成考试"};
    private static final String[] GENERATE_COURSE_KW = {"建课", "新建课程", "创建课程", "课程大纲", "生成课程", "设计课程"};
    private static final String[] DIAGNOSE_PLAN_KW = {"诊断", "学情", "学习规划", "怎么学", "学习计划", "薄弱"};
    private static final String[] EXERCISE_ASSESS_KW = {"练习", "自测", "做题", "测评", "交卷", "出几道", "随堂测"};
    private static final String[] NOTE_SIGN_QA_KW = {"签到", "笔记", "积分", "提问", "问答"};
    private static final String[] QUERY_LEARNING_KW = {"学得怎么样", "学习进度", "我的统计", "学习状态"};
    private static final String[] KB_KW = {"知识库", "根据讲义", "讲义内容", "查一下资料"};
    private static final String[] TASK_KW = {"定时任务", "提醒我", "到了提醒", "定时"};

    /**
     * 路由：返回意图决策（可能 UNKNOWN）
     *
     * @param userId    用户
     * @param userType  1 学生 / 2 老师
     * @param sessionId 会话（可为 null）
     * @param text      用户消息
     * @param agentType 当前个人 agent：student-agent / teacher-agent
     */
    public IntentDecision route(Long userId, Integer userType, Long sessionId, String text, String agentType) {
        if (StrUtil.isBlank(text)) {
            return IntentDecision.unknown();
        }
        boolean teacher = userType != null && userType == 2;

        // 学生向老师提问（含 IM/定时编排，student-agent 主用）
        if (!teacher && containsAny(text, ASK_TEACHER_KW)) {
            return IntentDecision.rule(Intent.ASK_TEACHER,
                    "本轮意图=向老师提问，优先走 ask-teacher 编排（邀请 teacher-agent），不要自行编造老师答复。");
        }
        // 老师出题/试卷（题库面，仅老师身份；触发 exam-agent 高危邀请 → 动作级 HITL）
        if (teacher && containsAny(text, GENERATE_EXAM_KW)) {
            return IntentDecision.rule(Intent.GENERATE_EXAM,
                    "本轮意图=生成考试资源，调度 exam-agent（题库出题），先出大纲给老师确认再落库。");
        }
        // 老师建课/大纲（course-agent）
        if (teacher && containsAny(text, GENERATE_COURSE_KW)) {
            return IntentDecision.rule(Intent.GENERATE_COURSE,
                    "本轮意图=生成课程资源，调度 course-agent（建课/大纲/章节），先出方案给老师确认。");
        }
        // 知识库问答
        if (containsAny(text, KB_KW)) {
            return IntentDecision.rule(Intent.KB_RAG, "本轮意图=知识库问答，优先使用知识库检索回答，不要编造。");
        }
        // 诊断/规划（learning 管道①②）
        if (containsAny(text, DIAGNOSE_PLAN_KW)) {
            return IntentDecision.rule(Intent.DIAGNOSE_PLAN,
                    "本轮意图=学情诊断/学习规划，调度 learning-agent 的 diagnose/plan 管道。");
        }
        // 练习/自测/测评（learning 管道③④：仅知识库出题，禁止触题库）
        if (containsAny(text, EXERCISE_ASSESS_KW)) {
            return IntentDecision.rule(Intent.EXERCISE_ASSESS,
                    "本轮意图=练习/自测/测评，走 AI 自测管道（仅课程知识库出题，严禁题库/exam 工具，防透题）。");
        }
        // 笔记/签到/积分/问答
        if (containsAny(text, NOTE_SIGN_QA_KW)) {
            return IntentDecision.rule(Intent.NOTE_SIGN_QA, "本轮意图=学习行为（笔记/签到/积分/问答），调用对应 learning 工具并基于真实数据回答。");
        }
        // 学习状态查询
        if (containsAny(text, QUERY_LEARNING_KW)) {
            return IntentDecision.rule(Intent.QUERY_LEARNING, "本轮意图=查询学习状态，调用 learning 读接口返回真实数据。");
        }
        // 定时任务
        if (containsAny(text, TASK_KW)) {
            return IntentDecision.rule(Intent.TASK_MANAGE, "本轮意图=定时任务管理，使用 task 工具。");
        }
        // ② LLM 分类层（规则未命中时；失败 → UNKNOWN 自由 ReAct 兜底）
        return classifier.classify(userType, agentType, text).orElseGet(IntentDecision::unknown);
    }

    private static boolean containsAny(String text, String[] kws) {
        for (String kw : kws) {
            if (text.contains(kw)) {
                return true;
            }
        }
        return false;
    }
}
