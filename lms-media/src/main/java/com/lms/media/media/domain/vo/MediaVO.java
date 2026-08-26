package com.lms.media.media.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 媒资出参（上传返回/详情/我的列表共用）
 *
 * 使用场景：上传成功返回、按 id 查详情、我的媒资分页，字段即前端渲染所需。
 */
@Data
@Schema(description = "媒资信息")
public class MediaVO {

    /** 媒资 id */
    private Long id;

    /** 原文件名 */
    private String name;

    /** 媒资类型：1 图片 / 2 视频 / 3 其他，取值见 MediaType 枚举 */
    private Integer type;

    /** 访问 URL（可直接预览/播放/下载） */
    private String url;

    /** 文件大小（单位：字节） */
    private Long size;

    /** MIME 类型 */
    private String mime;

    /** 创建时间 */
    private LocalDateTime createTime;
}
