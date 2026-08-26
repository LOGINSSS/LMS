package com.lms.media.media.service;

import com.lms.common.domain.dto.PageDTO;
import com.lms.media.media.domain.query.MediaPageQuery;
import com.lms.media.media.domain.vo.MediaVO;
import org.springframework.web.multipart.MultipartFile;

/**
 * 媒资业务接口
 *
 * 职责：文件/视频上传、我的媒资分页、详情查询与删除。
 * 权限规则：上传与分页均为当前登录用户视角（UserContext）；删除仅限本人。
 */
public interface IMediaService {

    /** 上传文件/视频，返回媒资 VO（含访问 URL） */
    MediaVO upload(MultipartFile file);

    /** 当前用户媒资分页（可选按类型筛选） */
    PageDTO<MediaVO> queryMyPage(MediaPageQuery query);

    /** 媒资详情（按 id 查询） */
    MediaVO getDetail(Long id);

    /** 删除媒资（逻辑删除，仅限本人） */
    void delete(Long id);
}
