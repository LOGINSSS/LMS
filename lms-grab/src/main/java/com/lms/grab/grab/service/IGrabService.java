package com.lms.grab.grab.service;

import com.lms.grab.grab.domain.dto.GrabStatusVO;

/**
 * 抢课业务服务
 *
 * 职责：抢课主流程（Redis 预检 → 落审计记录 → Kafka 异步落库）、
 * 抢课状态查询、库存预热（供 lms-course 发布后调用）。
 */
public interface IGrabService {

    /**
     * 库存预热（lms-course 发布成功后 Feign 调用）
     *
     * @param courseId       课程 id
     * @param grabStartTime  抢课开始时间
     * @param grabEndTime    抢课结束时间
     * @param stock          总名额（0=不限）
     */
    void prepare(Long courseId, String grabStartTime, String grabEndTime, Integer stock);

    /**
     * 学生抢课
     *
     * @param courseId 课程 id
     * @return 抢课记录 id
     */
    Long grab(Long courseId);

    /**
     * 抢课状态（剩余库存/是否已抢/窗口时间）
     *
     * @param courseId 课程 id
     * @return 抢课状态
     */
    GrabStatusVO status(Long courseId);
}
