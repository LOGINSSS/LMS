package com.lms.statistics.statistics.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

/**
 * 用户简要信息（跨服务传输对象）
 *
 * 用途：数据中心从 lms-user 拉用户分页时只需 id 与总数（PageDTO.total）。
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class UserBriefDTO {

    /** 用户档案 id */
    private Long id;
}
