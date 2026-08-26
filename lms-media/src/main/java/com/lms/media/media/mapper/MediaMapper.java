package com.lms.media.media.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lms.media.media.domain.po.Media;
import org.apache.ibatis.annotations.Mapper;

/**
 * 媒资表 Mapper
 *
 * 继承 BaseMapper 获得通用 CRUD，复杂统计后续按需追加自定义 SQL。
 */
@Mapper
public interface MediaMapper extends BaseMapper<Media> {
}
