package com.lms.ai.infrastructure.memory;

import com.lms.ai.application.port.out.LongTermMemoryPort;
import com.lms.ai.application.port.out.PersonalWikiPort;
import com.lms.ai.config.ReMeProperties;
import com.lms.common.exceptions.CommonException;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ReMeMemoryAdapterTest {

    @Test
    void disabledReMeUsesProfileOnly() {
        ReMeProperties properties = properties(false);
        ProfileMemoryAdapter profiles = profile("画像记忆");
        CapturingClient client = new CapturingClient();
        ReMeMemoryAdapter adapter = new ReMeMemoryAdapter(properties, profiles, client);

        assertThat(adapter.recall(recall())).containsExactly(
                new LongTermMemoryPort.MemoryFragment("profile", "画像记忆"));
        assertThat(client.calls).isEmpty();
    }

    @Test
    void recallsTopFiveWikiResultsAndKeepsProfileFallback() {
        ReMeProperties properties = properties(true);
        ProfileMemoryAdapter profiles = profile("画像记忆");
        CapturingClient client = new CapturingClient();
        client.searchAnswer = "相关 Wiki 记忆";
        ReMeMemoryAdapter adapter = new ReMeMemoryAdapter(properties, profiles, client);

        assertThat(adapter.recall(recall())).containsExactly(
                new LongTermMemoryPort.MemoryFragment("wiki", "相关 Wiki 记忆"),
                new LongTermMemoryPort.MemoryFragment("profile", "画像记忆"));
        assertThat(client.searchLimit).isEqualTo(5);
        assertThat(client.userId).isEqualTo(7L);
    }

    @Test
    void recallFailureDoesNotBlockProfileMemory() {
        ReMeProperties properties = properties(true);
        ProfileMemoryAdapter profiles = profile("画像记忆");
        CapturingClient client = new CapturingClient();
        client.fail = true;
        ReMeMemoryAdapter adapter = new ReMeMemoryAdapter(properties, profiles, client);

        assertThat(adapter.recall(recall())).containsExactly(
                new LongTermMemoryPort.MemoryFragment("profile", "画像记忆"));
    }

    @Test
    void profileFailureDoesNotDiscardReMeMemory() {
        ReMeProperties properties = properties(true);
        var service = mock(com.lms.ai.memory.ProfileService.class);
        when(service.profileInjection(7L)).thenThrow(new IllegalStateException("db down"));
        CapturingClient client = new CapturingClient();
        client.searchAnswer = "相关 Wiki 记忆";
        ReMeMemoryAdapter adapter = new ReMeMemoryAdapter(properties, new ProfileMemoryAdapter(service), client);

        assertThat(adapter.recall(recall())).containsExactly(
                new LongTermMemoryPort.MemoryFragment("wiki", "相关 Wiki 记忆"));
    }

    @Test
    void wikiPathIsGeneratedServerSideAndReusedForCorrectionAndDeletion() {
        ReMeProperties properties = properties(true);
        CapturingClient client = new CapturingClient();
        ReMeMemoryAdapter adapter = new ReMeMemoryAdapter(properties, profile(""), client);

        PersonalWikiPort.MemoryRef ref = adapter.remember(
                new PersonalWikiPort.RememberCommand(7L, "偏好", "我喜欢示例", "学习偏好"));
        adapter.correct(new PersonalWikiPort.CorrectCommand(7L, ref.memoryId(), "示例", "完整示例"));
        adapter.forget(new PersonalWikiPort.ForgetCommand(7L, ref.memoryId()));

        assertThat(ref.memoryId()).matches("[0-9a-f-]{36}");
        assertThat(client.path).isEqualTo("digest/wiki/" + ref.memoryId() + ".md");
        assertThat(client.name).isEqualTo("偏好");
        assertThat(client.description).isEqualTo("学习偏好");
        assertThat(client.content).isEqualTo("我喜欢示例");
        assertThat(client.oldText).isEqualTo("示例");
        assertThat(client.newText).isEqualTo("完整示例");
        assertThat(client.deletedPath).isEqualTo(client.path);
    }

    @Test
    void rejectsInvalidMemoryIdAndDisabledWrites() {
        CapturingClient client = new CapturingClient();
        ReMeMemoryAdapter enabled = new ReMeMemoryAdapter(properties(true), profile(""), client);
        ReMeMemoryAdapter disabled = new ReMeMemoryAdapter(properties(false), profile(""), client);

        assertThatThrownBy(() -> enabled.forget(
                new PersonalWikiPort.ForgetCommand(7L, "../other-user")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> disabled.remember(
                new PersonalWikiPort.RememberCommand(7L, "标题", "正文", null)))
                .isInstanceOf(CommonException.class);
    }

    private LongTermMemoryPort.RecallRequest recall() {
        return new LongTermMemoryPort.RecallRequest(7L, 1, "student-agent", "怎么学习", 99);
    }

    private ReMeProperties properties(boolean enabled) {
        ReMeProperties properties = new ReMeProperties();
        properties.setEnabled(enabled);
        properties.setEndpointTemplate("http://reme.internal/users/{userId}");
        return properties;
    }

    private ProfileMemoryAdapter profile(String text) {
        var service = mock(com.lms.ai.memory.ProfileService.class);
        when(service.profileInjection(7L)).thenReturn(text);
        return new ProfileMemoryAdapter(service);
    }

    private static final class CapturingClient implements ReMeJobClient {
        private final List<String> calls = new ArrayList<>();
        private boolean fail;
        private Long userId;
        private int searchLimit;
        private String searchAnswer;
        private String path;
        private String name;
        private String description;
        private String content;
        private String oldText;
        private String newText;
        private String deletedPath;

        @Override
        public String search(Long userId, String query, int limit) {
            calls.add("search");
            if (fail) throw new IllegalStateException("reme down");
            this.userId = userId;
            this.searchLimit = limit;
            return searchAnswer;
        }

        @Override
        public void write(Long userId, String path, String name, String description, String content) {
            calls.add("write");
            this.userId = userId;
            this.path = path;
            this.name = name;
            this.description = description;
            this.content = content;
        }

        @Override
        public void edit(Long userId, String path, String oldText, String newText) {
            calls.add("edit");
            this.path = path;
            this.oldText = oldText;
            this.newText = newText;
        }

        @Override
        public void delete(Long userId, String path) {
            calls.add("delete");
            this.deletedPath = path;
        }

        @Override
        public void autoMemory(Long userId, String sessionId, List<SourceMessage> messages, String memoryHint) {
            calls.add("auto_memory");
        }
    }
}
