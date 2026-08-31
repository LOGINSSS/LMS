package com.lms.course.course.constants;

import com.lms.common.constants.ErrorInfo;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 课程服务错误码（业务错误码 2201 起，全局唯一）
 */
@Getter
@AllArgsConstructor
public enum CourseErrorInfo implements ErrorInfo {

    /** 按 id 查询或操作不存在的课程时抛出 */
    COURSE_NOT_FOUND(2201, "课程不存在"),

    /** 学生选课/查看时课程尚未发布（status != 1） */
    COURSE_NOT_PUBLISHED(2202, "课程未发布"),

    /** 重复选课：同一学生同一课程已存在选课中记录 */
    ALREADY_ENROLLED(2203, "已选过该课程"),

    /** 退课时不存在选课中记录 */
    NOT_ENROLLED(2204, "未选该课程"),

    /** 课程插入/更新落库失败 */
    COURSE_SAVE_FAILED(2205, "课程保存失败"),

    /** 目录节点不存在 */
    CATALOG_NOT_FOUND(2206, "章节不存在"),

    /** 目录层级/父节点校验失败（节必须挂在章下） */
    CATALOG_PARENT_INVALID(2207, "父章节不合法"),

    /** 课程已发布，内容不可再编辑 */
    COURSE_LOCKED(2208, "课程已发布，内容不可编辑"),

    /** 抢课窗口未开放 */
    GRAB_NOT_OPEN(2209, "抢课未开始或已结束"),

    /** 抢课库存不足 */
    GRAB_SOLD_OUT(2210, "课程名额已抢完"),

    /** 已抢过该课程 */
    GRAB_DUPLICATED(2211, "已抢过该课程"),

    /** 抢课窗口时间不合法（开始 ≥ 结束） */
    GRAB_WINDOW_INVALID(2212, "抢课时间不合法"),

    /** 抢课窗口内的课程需走抢课接口 */
    GRAB_REQUIRED(2213, "该课程需通过抢课获取名额");

    private final int code;
    private final String msg;
}
