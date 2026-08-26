package com.lms.user.user.domain.query;

import com.lms.common.domain.query.PageQuery;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 用户分页查询条件
 *
 * 使用场景：管理端 GET /admin/users/page 的筛选入参，继承 PageQuery 的分页参数，
 * 支持按用户类型精确筛选 + 关键字模糊匹配（昵称 / 手机号 / 邮箱）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "用户分页查询条件")
public class UserPageQuery extends PageQuery {

    /** 用户类型筛选（可选）：1 学生 / 2 教师，取值见 UserType 枚举，为空则查全部 */
    @Schema(description = "用户类型：1学生 2教师")
    private Integer userType;

    /** 关键字（可选）：模糊匹配 nickname / phone / email 任一字段，为空则不按关键字筛选 */
    @Schema(description = "关键字：模糊匹配昵称/手机号/邮箱")
    private String keyword;
}
