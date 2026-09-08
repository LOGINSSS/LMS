package com.lms.course.course.service;

import com.lms.course.course.domain.dto.SlotForm;
import com.lms.course.course.domain.po.CourseScheduleSlot;
import com.lms.course.course.domain.vo.ClassEventVO;

import java.time.LocalDate;
import java.util.List;

/**
 * 课程排课服务（课表/日历数据面）
 */
public interface IScheduleService {

    /** 添加排课模板（教师本人课程） */
    void addSlot(Long courseId, SlotForm form);

    /** 排课模板列表（教师管理视图） */
    List<CourseScheduleSlot> listTemplates(Long courseId);

    /** 删除排课模板（教师本人课程） */
    void removeSlot(Long courseId, Long slotId);

    /**
     * 按课程集 + 日期区间展开上课事件（周循环/单周双周/单次），按日期时间排序
     *
     * @param courseIds 课程 id 集合（学生已报名课程 / 日历聚合）
     * @param start     区间开始（含）
     * @param end       区间结束（含）
     */
    List<ClassEventVO> expand(List<Long> courseIds, LocalDate start, LocalDate end);
}
