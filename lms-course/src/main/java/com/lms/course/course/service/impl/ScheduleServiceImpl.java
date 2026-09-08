package com.lms.course.course.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lms.common.enums.UserType;
import com.lms.common.exceptions.CommonException;
import com.lms.common.exceptions.ForbiddenException;
import com.lms.common.utils.AssertUtils;
import com.lms.common.utils.UserContext;
import com.lms.course.course.domain.dto.SlotForm;
import com.lms.course.course.domain.po.Course;
import com.lms.course.course.domain.po.CourseScheduleSlot;
import com.lms.course.course.domain.vo.ClassEventVO;
import com.lms.course.course.mapper.CourseMapper;
import com.lms.course.course.mapper.CourseScheduleSlotMapper;
import com.lms.course.course.service.IScheduleService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.time.temporal.IsoFields;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 课程排课服务实现（课表/日历数据面）
 *
 * 模板 → 事件展开规则：
 * - 每周(1)：区间内与 dayOfWeek 相同的每个日期；
 * - 单周(2)/双周(3)：以"自然周序号奇/偶"约定判定（date 所在周序号 %2：单周=奇）；
 * - 单次(4)：仅 dateOverride（需落在区间内）。
 * 单/双周的具体学期口径（以学期第几周计）如与你校历不一致，可在此替换 parity 实现，方便微调。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ScheduleServiceImpl implements IScheduleService {

    private static final long MAX_SPAN_DAYS = 366;

    private final CourseScheduleSlotMapper slotMapper;
    private final CourseMapper courseMapper;

    @Override
    public void addSlot(Long courseId, SlotForm form) {
        assertOwner(courseId);
        AssertUtils.notNull(form.getStartTime() != null && form.getEndTime() != null
                && form.getEndTime().isAfter(form.getStartTime()), "结束时间必须晚于开始时间");
        int weekType = form.getWeekType() == null ? CourseScheduleSlot.WEEK_EVERY : form.getWeekType();
        if (weekType == CourseScheduleSlot.WEEK_ONCE) {
            AssertUtils.notNull(form.getDateOverride(), "单次排课必须填日期");
            AssertUtils.isTrue(form.getDateOverride() != null
                            && form.getDateOverride().getDayOfWeek().getValue() == form.getDayOfWeek(),
                    "单次排课的星期需与日期一致");
        }
        CourseScheduleSlot slot = new CourseScheduleSlot();
        slot.setCourseId(courseId);
        slot.setLessonId(form.getLessonId());
        slot.setWeekType(weekType);
        slot.setDayOfWeek(form.getDayOfWeek());
        slot.setStartTime(form.getStartTime());
        slot.setEndTime(form.getEndTime());
        slot.setLocation(form.getLocation());
        slot.setDateOverride(weekType == CourseScheduleSlot.WEEK_ONCE ? form.getDateOverride() : null);
        slotMapper.insert(slot);
    }

    @Override
    public List<CourseScheduleSlot> listTemplates(Long courseId) {
        assertOwner(courseId);
        return slotMapper.selectList(new LambdaQueryWrapper<CourseScheduleSlot>()
                .eq(CourseScheduleSlot::getCourseId, courseId)
                .orderByAsc(CourseScheduleSlot::getDayOfWeek)
                .orderByAsc(CourseScheduleSlot::getStartTime));
    }

    @Override
    public void removeSlot(Long courseId, Long slotId) {
        assertOwner(courseId);
        CourseScheduleSlot slot = slotMapper.selectById(slotId);
        AssertUtils.notNull(slot, "排课不存在");
        AssertUtils.isTrue(Objects.equals(slot.getCourseId(), courseId), "排课不属于该课程");
        slotMapper.deleteById(slotId);
    }

    @Override
    public List<ClassEventVO> expand(List<Long> courseIds, LocalDate start, LocalDate end) {
        if (courseIds == null || courseIds.isEmpty() || start == null || end == null || end.isBefore(start)) {
            return List.of();
        }
        if (ChronoUnit.DAYS.between(start, end) > MAX_SPAN_DAYS) {
            throw new CommonException("查询区间过大（最多 366 天）");
        }
        List<CourseScheduleSlot> slots = slotMapper.selectList(new LambdaQueryWrapper<CourseScheduleSlot>()
                .in(CourseScheduleSlot::getCourseId, courseIds)
                .orderByAsc(CourseScheduleSlot::getCourseId));
        if (slots.isEmpty()) {
            return List.of();
        }
        Map<Long, String> courseNames = courseMapper.selectBatchIds(courseIds).stream()
                .collect(Collectors.toMap(Course::getId, Course::getName, (a, b) -> a));

        List<ClassEventVO> events = new ArrayList<>();
        for (CourseScheduleSlot s : slots) {
            Integer type = s.getWeekType() == null ? CourseScheduleSlot.WEEK_EVERY : s.getWeekType();
            if (type == CourseScheduleSlot.WEEK_ONCE) {
                if (s.getDateOverride() != null && !s.getDateOverride().isBefore(start)
                        && !s.getDateOverride().isAfter(end)) {
                    events.add(toEvent(s, s.getDateOverride(), courseNames));
                }
                continue;
            }
            LocalDate cursor = start;
            while (!cursor.isAfter(end)) {
                boolean dayMatch = cursor.getDayOfWeek().getValue() == s.getDayOfWeek();
                boolean parityOk = switch (type) {
                    case CourseScheduleSlot.WEEK_ODD -> cursor.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR) % 2 == 1;
                    case CourseScheduleSlot.WEEK_EVEN -> cursor.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR) % 2 == 0;
                    default -> true;
                };
                if (dayMatch && parityOk) {
                    events.add(toEvent(s, cursor, courseNames));
                }
                cursor = cursor.plusDays(1);
            }
        }
        events.sort(Comparator.comparing(ClassEventVO::getDate)
                .thenComparing(ClassEventVO::getStartTime)
                .thenComparing(ClassEventVO::getCourseId));
        return events;
    }

    private ClassEventVO toEvent(CourseScheduleSlot s, LocalDate date, Map<Long, String> names) {
        ClassEventVO vo = new ClassEventVO();
        vo.setCourseId(s.getCourseId());
        vo.setCourseName(names.getOrDefault(s.getCourseId(), "课程" + s.getCourseId()));
        vo.setLessonId(s.getLessonId());
        vo.setDate(date);
        vo.setDayOfWeek(date.getDayOfWeek().getValue());
        vo.setStartTime(s.getStartTime());
        vo.setEndTime(s.getEndTime());
        vo.setLocation(s.getLocation());
        vo.setWeekType(s.getWeekType());
        return vo;
    }

    private void assertOwner(Long courseId) {
        Long userId = UserContext.getUser();
        Integer userType = UserContext.getUserType();
        if (userId == null || userType == null || userType != UserType.TEACHER.getValue()) {
            throw new ForbiddenException("仅教师可管理排课");
        }
        Course course = courseMapper.selectById(courseId);
        AssertUtils.notNull(course, "课程不存在");
        if (!Objects.equals(course.getTeacherId(), userId)) {
            throw new ForbiddenException("只能管理自己创建的课程");
        }
    }
}
