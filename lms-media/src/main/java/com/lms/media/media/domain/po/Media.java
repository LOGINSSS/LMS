package com.lms.media.media.domain.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.lms.common.domain.po.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 媒资实体
 *
 * 对应表 media。业务模型：文件/视频统一上传与管理，
 * 每条媒资归属一个上传者（user_id），类型按扩展名归类（见 MediaType 枚举）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("media")
public class Media extends BaseEntity {

    /** 媒资 id（自增主键） */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 上传者用户 id（lms_user.user.id），关联上传用户 */
    private Long userId;

    /** 原文件名（上传时前端传入，含扩展名） */
    private String name;

    /** 媒资类型：1 图片 / 2 视频 / 3 其他，取值见 MediaType 枚举 */
    private Integer type;

    /** 访问 URL（静态映射 /uploads/**，可直达文件） */
    private String url;

    /** 文件大小（单位：字节） */
    private Long size;

    /** MIME 类型（来自上传请求，如 image/jpeg、video/mp4） */
    private String mime;

    /** 状态：1 正常 / 0 禁用 */
    private Integer status;
}
