package com.lms.calendar.domain.vo;

import lombok.Data;

import java.util.Map;

/**
 * 统一日历事件（前端大日历/课表渲染契约）
 */
@Data
public class EventVO {

    /** 事件类型：class 上课 / exam 考试 / assignment 作业 / activity 活动 */
    private String type;

    /** 域内事件 id（courseId/排期 id 等） */
    private String refId;

    private String title;
    private String start; // LocalDateTime ISO
    private String end;   // LocalDateTime ISO
    private String color;
    private String location;

    /** 跳转目标：{path:"/exam-papers/1?scheduleId=2&bizType=1"} 等，由前端 router.push */
    private Map<String, Object> jump;

    /** 冗余信息（课程 id 等，前端按需取） */
    private Long courseId;
}
