package com.lms.common.domain.query;

import com.baomidou.mybatisplus.core.metadata.OrderItem;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lms.common.constants.Constant;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import lombok.Data;

/**
 * 分页请求参数（所有分页查询接口的入参基类）
 */
@Data
@Schema(description = "分页请求参数")
public class PageQuery {

    public static final Integer DEFAULT_PAGE_NUM = 1;
    public static final Integer DEFAULT_PAGE_SIZE = 20;

    @Schema(description = "页码", example = "1")
    @Min(value = 1, message = "页码不能小于1")
    private Integer pageNo = DEFAULT_PAGE_NUM;

    @Schema(description = "每页大小", example = "5")
    @Min(value = 1, message = "每页查询数量不能小于1")
    private Integer pageSize = DEFAULT_PAGE_SIZE;

    @Schema(description = "是否升序", example = "true")
    private Boolean isAsc = true;

    @Schema(description = "排序字段")
    private String sortBy;

    /**
     * 分页偏移量（手写 SQL 分页用）
     */
    public int from() {
        return (pageNo - 1) * pageSize;
    }

    /**
     * 转 MyBatis-Plus 分页对象，支持自定义排序
     */
    public <T> Page<T> toMpPage(OrderItem... orderItems) {
        Page<T> page = new Page<>(pageNo, pageSize);
        if (orderItems != null && orderItems.length > 0) {
            page.addOrder(orderItems);
        }
        return page;
    }

    /**
     * 转 MyBatis-Plus 分页对象，指定默认排序字段
     */
    public <T> Page<T> toMpPage(String defaultSortBy, boolean isAsc) {
        return toMpPage(new OrderItem().setColumn(defaultSortBy).setAsc(isAsc));
    }

    /**
     * 转 MyBatis-Plus 分页对象，默认按创建时间倒序
     */
    public <T> Page<T> toMpPageDefaultSortByCreateTimeDesc() {
        return toMpPage(Constant.DATA_FIELD_NAME_CREATE_TIME, false);
    }
}
