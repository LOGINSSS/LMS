package com.lms.remark.remark.service;

import com.lms.remark.remark.domain.vo.LikeStatusVO;

import java.util.List;
import java.util.Map;

/**
 * 点赞业务服务
 *
 * 承载跨课程/笔记/问答对象的通用点赞切换、总数统计与状态查询；
 * 当前用户取自 UserContext，点赞切换需登录，状态查询未登录视为未赞。
 */
public interface ILikeService {

    /**
     * 点赞/取消点赞（切换，幂等）
     *
     * 同一用户对同一对象重复调用会在已赞/未赞之间翻转，最终状态取决于调用次数。
     *
     * @param bizType 点赞对象类型（1课程/2笔记/3问答），取值见 BizType 枚举
     * @param bizId   点赞对象 id
     * @return 翻转后的点赞状态（是否已赞 + 点赞总数）
     * @throws CommonException 未登录、点赞对象类型/id 不合法
     */
    LikeStatusVO toggle(Integer bizType, Long bizId);

    /**
     * 查询点赞总数
     *
     * @param bizType 点赞对象类型
     * @param bizId   点赞对象 id
     * @return 已赞总数（status=1 的记录数）
     * @throws CommonException 点赞对象类型/id 不合法
     */
    Long count(Integer bizType, Long bizId);

    /**
     * 查询当前用户点赞状态
     *
     * @param bizType 点赞对象类型
     * @param bizId   点赞对象 id
     * @return 当前用户是否已赞 + 点赞总数
     * @throws CommonException 点赞对象类型/id 不合法
     */
    LikeStatusVO status(Integer bizType, Long bizId);

    /**
     * 批量查询当前用户点赞状态（列表页用，避免 N+1）
     *
     * @param bizType 点赞对象类型
     * @param bizIds  点赞对象 id 集合
     * @return 已赞的 bizId → true（未赞对象不出现在 Map 中，调用方按 false 处理）
     * @throws CommonException 点赞对象类型不合法
     */
    Map<Long, Boolean> statusBatch(Integer bizType, List<Long> bizIds);
}
