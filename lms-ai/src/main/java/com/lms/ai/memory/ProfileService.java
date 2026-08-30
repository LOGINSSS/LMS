package com.lms.ai.memory;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lms.ai.config.AiAgentProperties;
import com.lms.ai.config.AgentModelFactory;
import com.lms.common.utils.JsonUtils;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.message.UserMessage;
import io.agentscope.core.model.ChatResponse;
import io.agentscope.core.model.GenerateOptions;
import io.agentscope.core.model.Model;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * L2 画像服务（spec §4.3）
 *
 * - 画像主表一人一行；行为事件流水是画像的原料（只由行为事件 + 显式"记住"写入，防幻觉污染）
 * - 摘要生成：低频（每 N 条事件/每日）用 LLM 把行为流水压缩成 summary
 * - 习惯记忆示例：薄弱点/活跃时段在摘要里体现，回答前注入 system prompt（见 PersonalAgentFactory）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProfileService {

    /** 行为事件类型 */
    public static final String EVENT_CHAT = "chat";
    public static final String EVENT_ASK_TEACHER = "ask_teacher";
    public static final String EVENT_SIGN_IN = "sign_in";
    public static final String EVENT_SEARCH = "search";
    public static final String EVENT_LIKE = "like";
    public static final String EVENT_KB_UPLOAD = "kb_upload";

    /** 触发摘要压缩的行为事件阈值 */
    private static final int SUMMARY_EVENT_THRESHOLD = 20;

    private final AgentUserProfileMapper profileMapper;
    private final AgentUserBehaviorMapper behaviorMapper;
    private final AgentProfileJobMapper jobMapper;
    private final AgentModelFactory modelFactory;
    private final AiAgentProperties properties;

    /** 取/建画像（一人一行） */
    public AgentUserProfile getOrCreate(Long userId, Integer role, String displayName) {
        AgentUserProfile p = profileMapper.selectOne(new LambdaQueryWrapper<AgentUserProfile>()
                .eq(AgentUserProfile::getUserId, userId));
        if (p == null) {
            p = new AgentUserProfile();
            p.setUserId(userId);
            p.setRole(role == null ? 1 : role);
            p.setDisplayName(StrUtil.blankToDefault(displayName, "用户" + userId));
            profileMapper.insert(p);
        } else if (role != null && p.getRole() == null) {
            p.setRole(role);
            profileMapper.updateById(p);
        }
        return p;
    }

    public AgentUserProfile getProfile(Long userId) {
        return profileMapper.selectOne(new LambdaQueryWrapper<AgentUserProfile>()
                .eq(AgentUserProfile::getUserId, userId));
    }

    /** 记录行为事件（L2 原料），每 N 条触发一次摘要压缩 */
    public void recordBehavior(Long userId, String eventType, Map<String, Object> payload) {
        if (userId == null) {
            return;
        }
        AgentUserBehavior b = new AgentUserBehavior();
        b.setUserId(userId);
        b.setEventType(eventType);
        b.setPayload(JSONUtil.toJsonStr(payload == null ? Map.of() : payload));
        b.setCreateTime(LocalDateTime.now());
        behaviorMapper.insert(b);
        // 低频压缩：每 SUMMARY_EVENT_THRESHOLD 条事件触发一次（spec §4.3：每日/每 N 条）
        Long count = behaviorMapper.selectCount(new LambdaQueryWrapper<AgentUserBehavior>()
                .eq(AgentUserBehavior::getUserId, userId));
        if (count != null && count % SUMMARY_EVENT_THRESHOLD == 0) {
            requestSummary(userId);
        }
    }

    /** 显式"记住我"更新画像字段（agent 工具 memory.saveProfile 调用） */
    public void saveProfile(Long userId, Map<String, Object> fields) {
        AgentUserProfile p = getOrCreate(userId, null, null);
        if (fields.get("interests") != null) {
            p.setInterests(StrUtil.toString(fields.get("interests")));
        }
        if (fields.get("learningHabits") != null) {
            p.setLearningHabits(JsonUtils.toJsonStr(fields.get("learningHabits")));
        }
        if (fields.get("displayName") != null) {
            p.setDisplayName(StrUtil.toString(fields.get("displayName")));
        }
        if (fields.get("summary") != null) {
            p.setSummary(StrUtil.toString(fields.get("summary")));
        }
        profileMapper.updateById(p);
    }

    /** 登记画像生成任务（异步执行，避免阻塞对话） */
    public void requestSummary(Long userId) {
        AgentProfileJob job = new AgentProfileJob();
        job.setUserId(userId);
        job.setStatus(AgentProfileJob.STATUS_PENDING);
        job.setCreateTime(LocalDateTime.now());
        job.setUpdateTime(LocalDateTime.now());
        jobMapper.insert(job);
    }

    /**
     * 生成画像摘要：LLM 把最近行为流水压缩成摘要（独立 LLM 调用，spec §4.3/§10 防污染）
     */
    public String generateSummary(Long userId) {
        List<AgentUserBehavior> recent = behaviorMapper.selectList(new LambdaQueryWrapper<AgentUserBehavior>()
                .eq(AgentUserBehavior::getUserId, userId)
                .orderByDesc(AgentUserBehavior::getId)
                .last("LIMIT 100"));
        if (recent.isEmpty()) {
            return "暂无行为数据";
        }
        StringBuilder sb = new StringBuilder();
        for (AgentUserBehavior b : recent) {
            sb.append(b.getEventType()).append(": ").append(b.getPayload()).append("\n");
        }
        String prompt = """
                你是 LMS 平台的用户画像分析器。请把下面该用户最近的行为流水压缩成一段用户画像摘要
                （200 字内，JSON：{"habits":"学习习惯/活跃时段","weakPoints":"薄弱知识点","interests":"兴趣","summary":"一句话画像"}）。
                只输出 JSON，不要解释。

                行为流水：
                %s
                """.formatted(sb);
        String summary = callSummaryModel(prompt);
        AgentUserProfile p = getOrCreate(userId, null, null);
        p.setSummary(summary);
        profileMapper.updateById(p);
        return summary;
    }

    /** 待执行的画像生成任务（每日/周期调度调用，spec §4.3） */
    public int runPendingSummaries() {
        List<AgentProfileJob> jobs = jobMapper.selectList(new LambdaQueryWrapper<AgentProfileJob>()
                .eq(AgentProfileJob::getStatus, AgentProfileJob.STATUS_PENDING)
                .last("LIMIT 20"));
        int done = 0;
        for (AgentProfileJob job : jobs) {
            job.setStatus(AgentProfileJob.STATUS_RUNNING);
            job.setUpdateTime(LocalDateTime.now());
            jobMapper.updateById(job);
            try {
                generateSummary(job.getUserId());
                job.setStatus(AgentProfileJob.STATUS_DONE);
                job.setLastSummaryTime(LocalDateTime.now());
            } catch (Exception e) {
                log.error("画像摘要生成失败 userId={}", job.getUserId(), e);
                job.setStatus(AgentProfileJob.STATUS_FAILED);
            }
            job.setUpdateTime(LocalDateTime.now());
            jobMapper.updateById(job);
            done++;
        }
        return done;
    }

    /** 画像注入文本（回答前注入 system prompt，spec §4.3） */
    public String profileInjection(Long userId) {
        AgentUserProfile p = getProfile(userId);
        if (p == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        if (StrUtil.isNotBlank(p.getInterests())) {
            sb.append("用户兴趣: ").append(p.getInterests()).append("\n");
        }
        if (StrUtil.isNotBlank(p.getLearningHabits())) {
            sb.append("学习习惯: ").append(p.getLearningHabits()).append("\n");
        }
        if (StrUtil.isNotBlank(p.getSummary())) {
            sb.append("画像摘要: ").append(p.getSummary()).append("\n");
        }
        return sb.toString();
    }

    private String callSummaryModel(String prompt) {
        Model model = modelFactory.get(properties.getSummaryModel());
        List<Msg> msgs = List.of(new UserMessage(prompt));
        Flux<ChatResponse> flux = model.stream(msgs, List.of(), GenerateOptions.builder().build());
        return flux.flatMapIterable(ChatResponse::getContent)
                .filter(TextBlock.class::isInstance)
                .map(block -> ((TextBlock) block).getText())
                .collect(Collectors.joining())
                .block();
    }
}
