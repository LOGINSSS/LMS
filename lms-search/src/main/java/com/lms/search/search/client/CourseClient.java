package com.lms.search.search.client;

import com.lms.common.domain.R;
import com.lms.common.domain.dto.PageDTO;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * lms-course 服务客户端：分页拉取课程数据用于同步 ES 索引
 *
 * 注意：返回类型用 R<PageDTO<...>> 包装（与 lms-course 响应结构一致）；
 * 同步失败由 {@link CourseClientFallbackFactory} 降级返回空页，不中断主流程。
 */
@FeignClient(name = "lms-course", fallbackFactory = CourseClientFallbackFactory.class)
public interface CourseClient {

    /**
     * 分页查询已发布课程
     *
     * @param pageNo   页码
     * @param pageSize 每页大小
     * @return 统一响应体，data 为课程卡片分页
     */
    @GetMapping("/courses/page")
    R<PageDTO<CourseCardDTO>> queryPage(@RequestParam("pageNo") Integer pageNo,
                                        @RequestParam("pageSize") Integer pageSize);
}
