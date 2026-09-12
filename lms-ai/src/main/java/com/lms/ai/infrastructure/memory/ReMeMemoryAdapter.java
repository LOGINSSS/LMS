package com.lms.ai.infrastructure.memory;

import com.lms.ai.application.port.out.LongTermMemoryPort;
import com.lms.ai.application.port.out.PersonalWikiPort;
import com.lms.ai.config.ReMeProperties;
import com.lms.common.exceptions.CommonException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

/** Combines the MySQL profile with a file-native ReMe personal Wiki. */
@Slf4j
@Primary
@Component
public class ReMeMemoryAdapter implements LongTermMemoryPort, PersonalWikiPort {

    private static final int MAX_SEARCH_RESULTS = 5;
    private static final Pattern MEMORY_ID = Pattern.compile("[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}");

    private final ReMeProperties properties;
    private final ProfileMemoryAdapter profileMemory;
    private final ReMeJobClient reme;

    public ReMeMemoryAdapter(ReMeProperties properties, ProfileMemoryAdapter profileMemory, ReMeJobClient reme) {
        this.properties = properties;
        this.profileMemory = profileMemory;
        this.reme = reme;
    }

    @Override
    public List<MemoryFragment> recall(RecallRequest request) {
        List<MemoryFragment> memories = new ArrayList<>();
        if (properties.isEnabled() && request.query() != null && !request.query().isBlank()) {
            try {
                int limit = Math.max(1, Math.min(request.limit(), MAX_SEARCH_RESULTS));
                String answer = reme.search(requireUser(request.userId()), request.query(), limit);
                if (answer != null && !answer.isBlank()) {
                    memories.add(new MemoryFragment("wiki", answer));
                }
            } catch (Exception e) {
                log.warn("ReMe Wiki 召回失败，继续使用画像记忆 userId={} errorType={}",
                        request.userId(), e.getClass().getSimpleName());
            }
        }
        try {
            memories.addAll(profileMemory.recall(request));
        } catch (Exception e) {
            log.warn("画像记忆召回失败，保留 ReMe Wiki 结果 userId={} errorType={}",
                    request.userId(), e.getClass().getSimpleName());
        }
        return memories;
    }

    @Override
    public MemoryRef remember(RememberCommand command) {
        requireEnabled();
        Long userId = requireUser(command.userId());
        String title = requireText(command.title(), "记忆标题", 100);
        String content = requireText(command.content(), "记忆正文", 20_000);
        String description = command.description();
        if (description != null && description.length() > 500) {
            throw new IllegalArgumentException("记忆描述长度不能超过 500");
        }
        String memoryId = UUID.randomUUID().toString();
        runWrite(() -> reme.write(userId, path(memoryId), title,
                description == null ? "" : description, content.strip()), "写入");
        return new MemoryRef(memoryId);
    }

    @Override
    public void correct(CorrectCommand command) {
        requireEnabled();
        Long userId = requireUser(command.userId());
        String memoryId = requireMemoryId(command.memoryId());
        String oldText = requireText(command.oldText(), "待更正文本", 5_000);
        String newText = requireText(command.newText(), "更正后文本", 5_000);
        runWrite(() -> reme.edit(userId, path(memoryId), oldText, newText), "更正");
    }

    @Override
    public void forget(ForgetCommand command) {
        requireEnabled();
        Long userId = requireUser(command.userId());
        String memoryId = requireMemoryId(command.memoryId());
        runWrite(() -> reme.delete(userId, path(memoryId)), "删除");
    }

    private void runWrite(Runnable operation, String action) {
        try {
            operation.run();
        } catch (Exception e) {
            log.warn("ReMe Wiki {}失败 errorType={}", action, e.getClass().getSimpleName());
            throw new CommonException("个人 Wiki " + action + "失败，请稍后重试");
        }
    }

    private void requireEnabled() {
        if (!properties.isEnabled()) {
            throw new CommonException("个人 Wiki 尚未启用");
        }
    }

    private Long requireUser(Long userId) {
        if (userId == null || userId <= 0) {
            throw new IllegalArgumentException("userId 必须为正数");
        }
        return userId;
    }

    private String requireMemoryId(String memoryId) {
        if (memoryId == null || !MEMORY_ID.matcher(memoryId).matches()) {
            throw new IllegalArgumentException("memoryId 格式非法");
        }
        return memoryId;
    }

    private String requireText(String value, String field, int maxLength) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + "不能为空");
        }
        if (value.length() > maxLength) {
            throw new IllegalArgumentException(field + "长度不能超过 " + maxLength);
        }
        return value;
    }

    private String path(String memoryId) {
        return "digest/wiki/" + memoryId + ".md";
    }

}
