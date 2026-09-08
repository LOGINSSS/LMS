package com.lms.exam.exam.service;

import com.lms.exam.exam.domain.dto.ExamScheduleFormDTO;
import com.lms.exam.exam.domain.dto.PaperSubmitDTO;
import com.lms.exam.exam.domain.po.ExamSchedule;

import java.util.List;
import java.util.Map;

/**
 * 发布物排期服务（v1 收尾 P2b-③/④：作业/考试）
 */
public interface IExamScheduleService {

    /** 老师发布排期（作业/考试，挂已发布卷面），返回排期 id */
    Long publish(ExamScheduleFormDTO dto);

    /** 老师结束排期（owner） */
    void close(Long id);

    /** 我的发布物列表（按我的课程过滤，已发布且未截止；bizType 可空=全部），日历展示用 */
    List<ExamSchedule> listMine(List<Long> myCourseIds, Integer bizType);

    /** 我发布的排期列表（老师） */
    List<ExamSchedule> listByTeacher();

    /** 排期详情（已发布可见，或本人发布） */
    ExamSchedule detail(Long id);

    /** 学生按排期交卷：校验发布状态 + 时间窗口，委托卷面服务端确定性判分（作业允许多次重做） */
    Map<String, Object> submitToSchedule(Long id, PaperSubmitDTO dto);

    /** 考试异步提交（前端轨）：锁页交卷 → Kafka（幂等消费端判分回流），返回 submissionId */
    Map<String, Object> submitAsync(Long id, PaperSubmitDTO dto);
}
