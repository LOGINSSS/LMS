package com.lms.learning.learning.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.lms.common.utils.UserContext;
import com.lms.learning.learning.client.CourseOwnerClient;
import com.lms.learning.learning.domain.po.Notification;
import com.lms.learning.learning.domain.po.QaQuestion;
import com.lms.learning.learning.mapper.NotificationMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class NotificationServiceTest {

    @BeforeAll
    static void initializeMybatisMetadata() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), Notification.class);
    }

    @AfterEach
    void clearUserContext() {
        UserContext.removeUser();
    }

    @Test
    void teacherAnswerResolvesPendingTeacherNotificationBeforeNotifyingStudent() {
        NotificationMapper mapper = mock(NotificationMapper.class);
        NotificationService service = new NotificationService(mapper, mock(CourseOwnerClient.class));
        QaQuestion question = new QaQuestion();
        question.setId(19L);
        question.setCourseId(8L);
        question.setUserId(2L);
        question.setTitle("事务为什么没有生效");
        UserContext.setUser(1L);
        UserContext.setUserType(2);

        service.notifyStudentAnswered(question, "需要从代理对象调用事务方法");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Wrapper<Notification>> updateCaptor = ArgumentCaptor.forClass(Wrapper.class);
        verify(mapper).update(isNull(), updateCaptor.capture());
        assertThat(((LambdaUpdateWrapper<Notification>) updateCaptor.getValue())
                .getParamNameValuePairs().values())
                .contains(NotificationService.TYPE_TEACHER_QA_RESOLVED, "问题已回答，可查看详情");
        verify(mapper).insert(any(Notification.class));
    }
}
