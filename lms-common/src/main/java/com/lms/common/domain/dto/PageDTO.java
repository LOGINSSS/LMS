package com.lms.common.domain.dto;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

/**
 * 分页响应体
 */
@Data
@Schema(description = "分页响应")
public class PageDTO<T> {

    @Schema(description = "总条数")
    private Long total;

    @Schema(description = "当前页数据")
    private List<T> list;

    public static <T> PageDTO<T> of(Long total, List<T> list) {
        PageDTO<T> dto = new PageDTO<>();
        dto.setTotal(total);
        dto.setList(list);
        return dto;
    }

    public static <T> PageDTO<T> of(Page<T> page, List<T> list) {
        return of(page.getTotal(), list);
    }
}
