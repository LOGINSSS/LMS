package com.lms.learning.learning.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lms.learning.learning.domain.po.Note;
import org.apache.ibatis.annotations.Mapper;

/**
 * 笔记数据访问
 */
@Mapper
public interface NoteMapper extends BaseMapper<Note> {
}
