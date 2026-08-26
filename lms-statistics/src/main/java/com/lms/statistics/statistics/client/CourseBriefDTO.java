package com.lms.statistics.statistics.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

/**
 * 课程简要信息（跨服务传输对象）
 *
 * 用途：数据中心从 lms-course 拉课程分页时只需 id 与总数（PageDTO.total）。
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class CourseBriefDTO {

    /** 课程 id */
    private Long id;
}
