package com.lms.exam.exam.domain.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.lms.common.domain.po.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 考试提交实体（前端轨：Kafka 异步提交幂等落库，对应表 exam_submission）
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("exam_submission")
public class ExamSubmission extends BaseEntity {

    public static final int STATUS_PENDING = 0;
    public static final int STATUS_DONE = 1;
    public static final int STATUS_FAILED = 2;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 提交幂等键（客户端 UUID，唯一） */
    private String submissionId;

    /** 考试排期 id */
    private Long scheduleId;

    /** 作答学生 id */
    private Long userId;

    /** 卷面 id（排期挂载） */
    private Long paperId;

    /** 作答 JSON：[{questionId,userAnswer}] */
    private String answers;

    /** 答对题数 */
    private Integer correctCount;

    /** 得分 */
    private Integer score;

    /** 卷面总分 */
    private Integer totalScore;

    /** 0处理中 1完成 2失败 */
    private Integer status;

    /** 失败原因 */
    private String errorMsg;
}
