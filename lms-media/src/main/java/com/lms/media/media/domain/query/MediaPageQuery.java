package com.lms.media.media.domain.query;

import com.lms.common.domain.query.PageQuery;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 媒资分页查询参数
 *
 * 使用场景：我的媒资列表查询入参，固定按当前用户过滤，类型可选筛选。
 * 排序固定按创建时间倒序（PageQuery 约定），不支持前端任意排序字段。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "媒资分页查询参数")
public class MediaPageQuery extends PageQuery {

    /** 媒资类型筛选（可选，传 null 表示不过滤） */
    private Integer type;
}
