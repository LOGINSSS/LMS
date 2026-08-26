package com.lms.media.media.enums;

import com.lms.common.enums.BaseEnum;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 媒资类型枚举
 *
 * 业务含义：按文件扩展名归类，前端按类型差异化渲染（图片预览/视频播放/其他下载）。
 * 落库字段：media.type（TINYINT）。
 */
@Getter
@AllArgsConstructor
public enum MediaType implements BaseEnum {

    /** 图片：jpg/jpeg/png/gif/webp */
    IMAGE(1, "图片"),

    /** 视频：mp4/mov/avi/mkv */
    VIDEO(2, "视频"),

    /** 其他文件类型 */
    OTHER(3, "其他");

    private final int value;
    private final String desc;

    /**
     * 按枚举值查找类型，未知值抛参数异常
     */
    public static MediaType of(int value) {
        for (MediaType type : values()) {
            if (type.value == value) {
                return type;
            }
        }
        throw new IllegalArgumentException("未知的媒资类型: " + value);
    }
}
