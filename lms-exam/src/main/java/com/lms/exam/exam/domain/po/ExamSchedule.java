package com.lms.exam.exam.domain.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.lms.common.domain.po.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 发布物排期实体（v1 收尾 P2b-③/④，对应表 exam_schedule）
 *
 * 业务含义：老师把已发布卷面（exam_paper）挂到课程排期发布，biz_type 区分【考试】与【作业】：
 * - 作业（biz_type=2）：老师出卷流程（组卷→发布卷面）产物挂排期，泄题责任归老师；学生在截止前可多次作答（服务端判分）；
 * - 考试（biz_type=1）：开考-截止窗口作答（前端专用锁页 + Kafka 幂等提交为独立前端轨）。
 * 状态机：1 已发布 → 2 已结束（0 草稿预留）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("exam_schedule")
public class ExamSchedule extends BaseEntity {

    public static final int STATUS_DRAFT = 0;
    public static final int STATUS_PUBLISHED = 1;
    public static final int STATUS_CLOSED = 2;

    public static final int BIZ_EXAM = 1;
    public static final int BIZ_HOMEWORK = 2;

    /** 排期 id（自增主键） */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 发布物标题（考试/作业名） */
    private String title;

    /** 说明 */
    private String description;

    /** 发布物类型：1 考试 2 作业 */
    private Integer bizType;

    /** 挂载卷面 id（exam_paper，须已发布） */
    private Long paperId;

    /** 适用课程 id 集合（逗号分隔，可多课程） */
    private String courseIds;

    /** 发布老师 id */
    private Long teacherId;

    /** 作答时长（分钟） */
    private Integer durationMinutes;

    /** 开始时间（作业=可开始作答；考试=开考） */
    private LocalDateTime startTime;

    /** 截止时间（作业=提交截止；考试=考试结束） */
    private LocalDateTime endTime;

    /** 状态：1已发布 2已结束（0草稿预留） */
    private Integer status;
}
