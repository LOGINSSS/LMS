package com.lms.grab.grab.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lms.common.exceptions.CommonException;
import com.lms.common.utils.AssertUtils;
import com.lms.common.utils.UserContext;
import com.lms.grab.grab.constants.GrabErrorInfo;
import com.lms.grab.grab.domain.dto.GrabStatusVO;
import com.lms.grab.grab.domain.po.GrabRecord;
import com.lms.grab.grab.enums.GrabRecordStatus;
import com.lms.grab.grab.mapper.GrabRecordMapper;
import com.lms.grab.grab.service.GrabEventProducer;
import com.lms.grab.grab.service.GrabRedisService;
import com.lms.grab.grab.service.IGrabService;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 抢课业务服务实现
 *
 * 事务边界：抢课记录落库为单事务（记录审计），真正的选课落库在
 * lms-course 消费 Kafka 异步完成，本服务不跨库写。
 * 流程（spec 0.2 §4.3）：Redis Lua 预检（窗口/去重/扣减）→ 落 grab_record
 * → 发 Kafka → lms-course 幂等写 course_enrollment。
 */
@Service
@RequiredArgsConstructor
public class GrabServiceImpl implements IGrabService {

    private final GrabRedisService grabRedis;
    private final GrabRecordMapper grabRecordMapper;
    private final GrabEventProducer eventProducer;

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Override
    public void prepare(Long courseId, String grabStartTime, String grabEndTime, Integer stock) {
        //1. 时间格式校验（ISO 转字符串）
        LocalDateTime start = LocalDateTime.parse(grabStartTime);
        LocalDateTime end = LocalDateTime.parse(grabEndTime);
        if (!start.isBefore(end)) {
            throw new CommonException(GrabErrorInfo.GRAB_NOT_OPEN);
        }
        //2. 预热 Redis（幂等，重复预热重置库存）
        grabRedis.prepare(courseId, start, end, stock);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long grab(Long courseId) {
        //1. 身份校验：仅学生可抢课
        Long userId = UserContext.getUser();
        AssertUtils.isNotNull(userId, "请先登录");
        LocalDateTime now = LocalDateTime.now();

        //2. Redis 原子预检：窗口 + 去重 + 扣减（单次往返）
        int result = grabRedis.grab(courseId, userId, now);
        if (result == 0) {
            throw new CommonException(GrabErrorInfo.GRAB_DUPLICATED);
        }
        if (result == -1) {
            throw new CommonException(GrabErrorInfo.GRAB_NOT_OPEN);
        }
        if (result == -2) {
            throw new CommonException(GrabErrorInfo.GRAB_SOLD_OUT);
        }

        //3. 落审计记录（status=1 待落库；uk_course_user 防并发重复）
        GrabRecord record = new GrabRecord();
        record.setCourseId(courseId);
        record.setUserId(userId);
        record.setGrabTime(now);
        record.setSource(1);
        record.setStatus(GrabRecordStatus.PENDING.getValue());
        try {
            grabRecordMapper.insert(record);
        } catch (DuplicateKeyException e) {
            // 并发兜底：撞唯一键说明 Redis 已成功但重复落库，回补库存并拒绝
            throw new CommonException(GrabErrorInfo.GRAB_DUPLICATED);
        }

        //4. 发 Kafka：lms-course 消费端异步落库 course_enrollment
        eventProducer.publishSuccess(courseId, userId, now.format(FMT), record.getId());
        return record.getId();
    }

    @Override
    public GrabStatusVO status(Long courseId) {
        GrabStatusVO vo = new GrabStatusVO();
        vo.setCourseId(courseId);
        vo.setLeftStock(grabRedis.leftStock(courseId));
        Long userId = UserContext.getUser();
        vo.setGrabbed(userId != null && grabRedis.hasGrabbed(courseId, userId));
        // 窗口时间从 Redis Hash 读（未预热则为空）
        java.util.Map<Object, Object> window = grabRedis.window(courseId);
        vo.setGrabStartTime(window.get("start") == null ? null : String.valueOf(window.get("start")));
        vo.setGrabEndTime(window.get("end") == null ? null : String.valueOf(window.get("end")));
        return vo;
    }
}
