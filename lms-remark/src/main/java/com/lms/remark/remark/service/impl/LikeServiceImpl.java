package com.lms.remark.remark.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lms.common.exceptions.CommonException;
import com.lms.common.utils.AssertUtils;
import com.lms.common.utils.CollUtils;
import com.lms.common.utils.UserContext;
import com.lms.remark.remark.constants.LikeErrorInfo;
import com.lms.remark.remark.domain.po.LikedRecord;
import com.lms.remark.remark.domain.vo.LikeStatusVO;
import com.lms.remark.remark.enums.BizType;
import com.lms.remark.remark.mapper.LikedRecordMapper;
import com.lms.remark.remark.service.ILikeService;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 点赞业务服务实现
 *
 * 事务边界：点赞切换为单事务，插入/翻转任一失败整体回滚。
 * 幂等与并发：同一用户对同一对象只保留一条记录（唯一键 uk_user_biz），
 * 并发重复插入由 DuplicateKeyException 兜底后重查再翻转。
 * 当前用户来源：UserContext（公共拦截器解析网关透传的 user-info 头写入）。
 */
@Service
@RequiredArgsConstructor
public class LikeServiceImpl implements ILikeService {

    private final LikedRecordMapper likedRecordMapper;

    /** 点赞状态：已赞 */
    private static final int STATUS_LIKED = 1;

    /** 点赞状态：已取消 */
    private static final int STATUS_CANCELED = 0;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public LikeStatusVO toggle(Integer bizType, Long bizId) {
        //1. 身份校验：点赞需登录，未登录直接拒绝
        Long userId = UserContext.getUser();
        AssertUtils.isNotNull(userId, "请先登录");
        //2. 校验点赞对象：类型/id 非法抛业务异常
        BizType type = checkBizType(bizType);
        checkBizId(bizId);
        //3. 查当前用户对目标对象的历史记录（唯一键 uk_user_biz 保证最多一条）
        LikedRecord record = selectByUserAndBiz(userId, type.getValue(), bizId);
        //4. 记录存在则翻转状态（1→0、0→1），不存在则新增已赞记录
        boolean liked;
        if (record != null) {
            //4.1 翻转：取消点赞置 0，重新点赞置 1，保留历史记录
            record.setStatus(record.getStatus() == STATUS_LIKED ? STATUS_CANCELED : STATUS_LIKED);
            likedRecordMapper.updateById(record);
            liked = record.getStatus() == STATUS_LIKED;
        } else {
            //4.2 新增已赞记录；并发插入撞唯一键时兜底重查再翻转
            liked = insertOrFlipByConcurrency(userId, type.getValue(), bizId);
        }
        //5. 统计翻转后的点赞总数
        Long likeCount = countLiked(type.getValue(), bizId);
        //6. 组装返回
        LikeStatusVO vo = new LikeStatusVO();
        vo.setLiked(liked);
        vo.setLikeCount(likeCount);
        return vo;
    }

    @Override
    public Long count(Integer bizType, Long bizId) {
        //1. 校验点赞对象
        BizType type = checkBizType(bizType);
        checkBizId(bizId);
        //2. 统计已赞总数
        return countLiked(type.getValue(), bizId);
    }

    @Override
    public LikeStatusVO status(Integer bizType, Long bizId) {
        //1. 校验点赞对象
        BizType type = checkBizType(bizType);
        checkBizId(bizId);
        //2. 查当前用户是否已赞（未登录视为未赞，只读接口不做强制登录）
        Long userId = UserContext.getUser();
        boolean liked = userId != null && isLiked(userId, type.getValue(), bizId);
        //3. 统计点赞总数
        Long likeCount = countLiked(type.getValue(), bizId);
        //4. 组装返回
        LikeStatusVO vo = new LikeStatusVO();
        vo.setLiked(liked);
        vo.setLikeCount(likeCount);
        return vo;
    }

    @Override
    public Map<Long, Boolean> statusBatch(Integer bizType, List<Long> bizIds) {
        //1. 校验点赞对象类型（批量场景无单个 bizId，仅校验类型）
        BizType type = checkBizType(bizType);
        //2. 空集合直接返回空 Map，避免无意义查询
        if (CollUtils.isEmpty(bizIds)) {
            return Collections.emptyMap();
        }
        //3. 取当前用户：未登录视为全部未赞
        Long userId = UserContext.getUser();
        if (userId == null) {
            return Collections.emptyMap();
        }
        //4. 一次查询该 bizType 下当前用户所有已赞记录，避免 N+1
        List<LikedRecord> records = likedRecordMapper.selectList(new LambdaQueryWrapper<LikedRecord>()
                .eq(LikedRecord::getBizType, type.getValue())
                .eq(LikedRecord::getUserId, userId)
                .in(LikedRecord::getBizId, bizIds)
                .eq(LikedRecord::getStatus, STATUS_LIKED));
        //5. 组装 Map：已赞的 bizId → true（未赞对象不出现在 Map 中，调用方按 false 处理）
        Map<Long, Boolean> likedMap = new HashMap<>(records.size());
        for (LikedRecord record : records) {
            likedMap.put(record.getBizId(), true);
        }
        return likedMap;
    }

    /**
     * 校验点赞对象类型并返回枚举（未知类型抛 BIZ_TYPE_INVALID）
     */
    private BizType checkBizType(Integer bizType) {
        BizType type = BizType.of(bizType == null ? 0 : bizType);
        if (type == null) {
            throw new CommonException(LikeErrorInfo.BIZ_TYPE_INVALID);
        }
        return type;
    }

    /**
     * 校验点赞对象 id（null 或非正数抛 BIZ_ID_INVALID）
     */
    private void checkBizId(Long bizId) {
        if (bizId == null || bizId <= 0) {
            throw new CommonException(LikeErrorInfo.BIZ_ID_INVALID);
        }
    }

    /**
     * 按用户+对象类型+对象 id 查询点赞记录（唯一键 uk_user_biz 保证最多一条）
     */
    private LikedRecord selectByUserAndBiz(Long userId, Integer bizType, Long bizId) {
        return likedRecordMapper.selectOne(new LambdaQueryWrapper<LikedRecord>()
                .eq(LikedRecord::getUserId, userId)
                .eq(LikedRecord::getBizType, bizType)
                .eq(LikedRecord::getBizId, bizId));
    }

    /**
     * 新增已赞记录，并发插入撞唯一键（uk_user_biz）时兜底重查再翻转
     *
     * @return 翻转后是否已赞
     */
    private boolean insertOrFlipByConcurrency(Long userId, Integer bizType, Long bizId) {
        //1. 组装新增记录：默认已赞状态
        LikedRecord insert = new LikedRecord();
        insert.setUserId(userId);
        insert.setBizType(bizType);
        insert.setBizId(bizId);
        insert.setStatus(STATUS_LIKED);
        try {
            //2. 正常插入：本次为首次点赞
            likedRecordMapper.insert(insert);
            return true;
        } catch (DuplicateKeyException e) {
            //3. 【保障机制】并发兜底：uk_user_biz 撞键说明记录已被并发请求插入，重查后翻转
            LikedRecord exists = selectByUserAndBiz(userId, bizType, bizId);
            if (exists != null) {
                exists.setStatus(exists.getStatus() == STATUS_LIKED ? STATUS_CANCELED : STATUS_LIKED);
                likedRecordMapper.updateById(exists);
                return exists.getStatus() == STATUS_LIKED;
            }
            //4. 极端竞态：并发记录暂不可见（MVCC 快照），按已赞处理，避免接口报错
            return true;
        }
    }

    /**
     * 统计指定对象已赞总数（status=1）
     */
    private Long countLiked(Integer bizType, Long bizId) {
        return likedRecordMapper.selectCount(new LambdaQueryWrapper<LikedRecord>()
                .eq(LikedRecord::getBizType, bizType)
                .eq(LikedRecord::getBizId, bizId)
                .eq(LikedRecord::getStatus, STATUS_LIKED));
    }

    /**
     * 查询指定用户对指定对象是否已赞
     */
    private boolean isLiked(Long userId, Integer bizType, Long bizId) {
        Long count = likedRecordMapper.selectCount(new LambdaQueryWrapper<LikedRecord>()
                .eq(LikedRecord::getUserId, userId)
                .eq(LikedRecord::getBizType, bizType)
                .eq(LikedRecord::getBizId, bizId)
                .eq(LikedRecord::getStatus, STATUS_LIKED));
        return count != null && count > 0;
    }
}
