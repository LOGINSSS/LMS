package com.lms.remark.remark.domain.vo;

import lombok.Data;

/**
 * 点赞状态出参
 *
 * 使用场景：点赞切换、点赞状态查询接口统一返回，供前端渲染点赞按钮高亮与计数。
 */
@Data
public class LikeStatusVO {

    /** 当前用户是否已赞（true 已赞 / false 未赞） */
    private Boolean liked;

    /** 点赞总数（status=1 的记录数） */
    private Long likeCount;
}
