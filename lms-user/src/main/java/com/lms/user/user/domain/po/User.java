package com.lms.user.user.domain.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.lms.common.domain.po.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 用户档案主表实体
 *
 * 对应表 lms_user.user，承载账号注册后的基础档案：昵称、头像、联系方式等。
 * 业务含义：一个账号（lms_auth.account.id）对应一份用户档案，accountId 通过
 * uk_account_id 唯一键一对一关联；createTime / updateTime / deleted 等公共字段
 * 由 BaseEntity 提供（MyBatis-Plus 自动填充 + 逻辑删除）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("user")
public class User extends BaseEntity {

    /** 用户档案 id（自增主键） */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 关联账号 id：指向 lms_auth.account.id，唯一（uk_account_id），同时是创建档案的幂等键 */
    private Long accountId;

    /** 用户类型：1 学生 / 2 教师，取值见 UserType 枚举，决定写入哪张扩展表 */
    private Integer userType;

    /** 昵称 */
    private String nickname;

    /** 头像 URL */
    private String avatar;

    /** 手机号 */
    private String phone;

    /** 邮箱 */
    private String email;

    /** 状态：1 正常 / 0 禁用，档案创建时固定为 1 */
    private Integer status;
}
