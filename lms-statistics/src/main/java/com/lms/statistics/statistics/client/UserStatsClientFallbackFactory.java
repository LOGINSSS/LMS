package com.lms.statistics.statistics.client;

import com.lms.common.domain.R;
import com.lms.common.domain.dto.PageDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

import java.util.Collections;

/**
 * lms-user 用户统计降级工厂
 *
 * 业务规则：拉取失败返回 0/空页，本次聚合该维度记 0，不阻塞看板。
 */
@Slf4j
@Component
public class UserStatsClientFallbackFactory implements FallbackFactory<UserStatsClient> {

    @Override
    public UserStatsClient create(Throwable cause) {
        log.warn("调用 lms-user 统计失败：{}", cause == null ? "未知原因" : cause.getMessage());
        return new UserStatsClient() {
            @Override
            public R<PageDTO<UserBriefDTO>> queryUserPage(Integer pageNo, Integer pageSize) {
                return R.ok(PageDTO.of(0L, Collections.emptyList()));
            }

            @Override
            public R<Long> countTodayUsers() {
                return R.ok(0L);
            }
        };
    }
}
