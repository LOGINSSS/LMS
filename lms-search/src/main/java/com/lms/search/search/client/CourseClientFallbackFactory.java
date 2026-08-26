package com.lms.search.search.client;

import com.lms.common.domain.R;
import com.lms.common.domain.dto.PageDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

import java.util.Collections;

/**
 * lms-course 服务降级工厂
 *
 * 业务规则：同步索引时拉取课程失败（服务不可用/超时）返回空页，
 * 本次同步跳过该页，由下一次同步补齐，不阻塞同步主流程。
 */
@Slf4j
@Component
public class CourseClientFallbackFactory implements FallbackFactory<CourseClient> {

    @Override
    public CourseClient create(Throwable cause) {
        log.warn("调用 lms-course 拉取课程失败：{}", cause == null ? "未知原因" : cause.getMessage());
        return new CourseClient() {
            @Override
            public R<PageDTO<CourseCardDTO>> queryPage(Integer pageNo, Integer pageSize) {
                return R.ok(PageDTO.of(0L, Collections.emptyList()));
            }
        };
    }
}
