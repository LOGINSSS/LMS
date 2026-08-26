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
    COURSE_SAVE_FAILED(2205, "课程保存失败");

    private final int code;
    private final String msg;
}
