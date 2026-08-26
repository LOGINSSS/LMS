package com.lms.statistics.statistics.client;

import com.lms.common.domain.R;
import com.lms.common.domain.dto.PageDTO;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Collections;

/**
 * lms-user 用户统计客户端
 *
 * 用途：数据中心拉取用户总数与今日新增用户；
 * 失败由 {@link UserStatsClientFallbackFactory} 降级返回 0/空页，保证看板不因单服务故障中断。
 */
@FeignClient(name = "lms-user", fallbackFactory = UserStatsClientFallbackFactory.class)
public interface UserStatsClient {

    /**
     * 用户分页（拿 total 作为用户总数）
     *
     * @param pageNo   页码
     * @param pageSize 每页大小
     * @return 统一响应体，data 为用户分页
     */
    @GetMapping("/admin/users/page")
    R<PageDTO<UserBriefDTO>> queryUserPage(@RequestParam("pageNo") Integer pageNo,
                                           @RequestParam("pageSize") Integer pageSize);

    /**
     * 今日新增用户数
     *
     * @return 统一响应体，data 为今日新增数
     */
    @GetMapping("/admin/users/stats/today")
    R<Long> countTodayUsers();
}
