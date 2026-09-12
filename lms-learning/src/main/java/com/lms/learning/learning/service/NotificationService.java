package com.lms.learning.learning.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lms.common.domain.R;
import com.lms.common.domain.dto.PageDTO;
import com.lms.common.utils.AssertUtils;
import com.lms.common.utils.BeanUtils;
import com.lms.common.utils.CollUtils;
import com.lms.common.utils.UserContext;
import com.lms.learning.learning.client.CourseOwnerClient;
import com.lms.learning.learning.domain.po.Notification;
import com.lms.learning.learning.domain.po.QaQuestion;
import com.lms.learning.learning.domain.vo.NotificationVO;
import com.lms.learning.learning.mapper.NotificationMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * 站内消息（答疑通知信箱）服务
 *
 * 触发点（由学习主流程调用，均为 best-effort）：
 * - 学生提问：向课程归属教师写「待回答」（需经 Feign 查 lms-course 课程归属，失败静默）；
 * - 教师回答：向提问学生写「已被老师回答」。
 * 读取端：本人分页信箱、未读数、已读标记（仅本人）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    /** 消息类型：课程有学生提问待老师回答 */
    public static final int TYPE_TEACHER_QA = 1;
    /** 消息类型：提问已被老师回答 */
    public static final int TYPE_STUDENT_ANSWERED = 2;
    /** 消息类型：教师侧待回答通知已处理 */
    public static final int TYPE_TEACHER_QA_RESOLVED = 3;

    private final NotificationMapper notificationMapper;
    private final CourseOwnerClient courseOwnerClient;

    // ---------- 写入（事件钩子，best-effort） ----------

    /** 学生提问 → 通知课程归属教师（教师本人提问不发） */
    public void notifyTeacherNewQuestion(QaQuestion question) {
        if (question == null || question.getUserId() == null) {
            return;
        }
        try {
            OwnerInfo owner = ownerOf(question.getCourseId());
            if (owner == null || owner.teacherId == null || owner.teacherId.equals(question.getUserId())) {
                return;
            }
            String courseLabel = owner.name == null ? ("#" + question.getCourseId()) : owner.name;
            Notification n = new Notification();
            n.setUserId(owner.teacherId);
            n.setType(TYPE_TEACHER_QA);
            n.setCourseId(question.getCourseId());
            n.setQuestionId(question.getId());
            n.setTitle(cut(question.getTitle(), 120));
            n.setContent("课程《" + courseLabel + "》有新的学生提问，请及时回答");
            notificationMapper.insert(n);
        } catch (Exception e) {
            log.warn("提问通知写入失败（忽略）questionId={}: {}", question.getId(), e.getMessage());
        }
    }

    /** 教师回答 → 通知提问学生（仅教师身份触发；回答自己问题不发） */
    public void notifyStudentAnswered(QaQuestion question, String answerText) {
        if (question == null || question.getUserId() == null) {
            return;
        }
        try {
            Integer userType = UserContext.getUserType();
            Long current = UserContext.getUser();
            if (userType == null || userType != 2 || current == null || question.getUserId().equals(current)) {
                return;
            }
            notificationMapper.update(null, new LambdaUpdateWrapper<Notification>()
                    .eq(Notification::getQuestionId, question.getId())
                    .eq(Notification::getType, TYPE_TEACHER_QA)
                    .set(Notification::getType, TYPE_TEACHER_QA_RESOLVED)
                    .set(Notification::getIsRead, 1)
                    .set(Notification::getContent, "问题已回答，可查看详情"));
            Notification n = new Notification();
            n.setUserId(question.getUserId());
            n.setType(TYPE_STUDENT_ANSWERED);
            n.setCourseId(question.getCourseId());
            n.setQuestionId(question.getId());
            n.setTitle("你的提问已被老师回答：" + cut(question.getTitle(), 80));
            n.setContent(cut(answerText, 300));
            notificationMapper.insert(n);
        } catch (Exception e) {
            log.warn("回答通知写入失败（忽略）questionId={}: {}", question.getId(), e.getMessage());
        }
    }

    // ---------- 读取 / 已读（本人信箱） ----------

    public PageDTO<NotificationVO> pageMy(int pageNo, int pageSize) {
        Long userId = currentUser();
        Page<Notification> page = new Page<>(pageNo, pageSize);
        notificationMapper.selectPage(page, new LambdaQueryWrapper<Notification>()
                .eq(Notification::getUserId, userId)
                .orderByDesc(Notification::getId));
        List<NotificationVO> vos = BeanUtils.copyList(page.getRecords(), NotificationVO.class);
        return PageDTO.of(page.getTotal(), vos);
    }

    public int countUnread() {
        Long userId = currentUser();
        Long cnt = notificationMapper.selectCount(new LambdaQueryWrapper<Notification>()
                .eq(Notification::getUserId, userId)
                .eq(Notification::getIsRead, 0));
        return cnt == null ? 0 : cnt.intValue();
    }

    public void markRead(List<Long> ids) {
        if (CollUtils.isEmpty(ids)) {
            return;
        }
        Long userId = currentUser();
        notificationMapper.update(null, new LambdaUpdateWrapper<Notification>()
                .eq(Notification::getUserId, userId)
                .in(Notification::getId, ids)
                .set(Notification::getIsRead, 1));
    }

    public void markAllRead() {
        Long userId = currentUser();
        notificationMapper.update(null, new LambdaUpdateWrapper<Notification>()
                .eq(Notification::getUserId, userId)
                .eq(Notification::getIsRead, 0)
                .set(Notification::getIsRead, 1));
    }

    // ---------- 内部 ----------

    private Long currentUser() {
        Long userId = UserContext.getUser();
        AssertUtils.isNotNull(userId, "未登录");
        return userId;
    }

    /** 经 Feign 查课程归属教师与课程名（失败返回 null，调用方静默跳过） */
    private OwnerInfo ownerOf(Long courseId) {
        if (courseId == null) {
            return null;
        }
        R<Map<String, Object>> r = courseOwnerClient.owner(courseId);
        if (r == null || !r.success() || r.getData() == null) {
            return null;
        }
        Map<String, Object> data = r.getData();
        OwnerInfo o = new OwnerInfo();
        Object tid = data.get("teacherId");
        o.teacherId = tid == null ? null : Long.valueOf(String.valueOf(tid));
        Object name = data.get("name");
        o.name = name == null ? null : String.valueOf(name);
        return o;
    }

    private String cut(String s, int max) {
        if (s == null || s.isBlank()) {
            return "";
        }
        return s.length() <= max ? s : s.substring(0, max) + "…";
    }

    private static class OwnerInfo {
        Long teacherId;
        String name;
    }
}
