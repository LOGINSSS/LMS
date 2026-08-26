package com.lms.media.media.controller;

import com.lms.common.domain.R;
import com.lms.common.domain.dto.PageDTO;
import com.lms.media.media.domain.query.MediaPageQuery;
import com.lms.media.media.domain.vo.MediaVO;
import com.lms.media.media.service.IMediaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 媒资接口（文件/视频上传与管理）
 *
 * 职责：接收上传文件与查询/删除请求，参数校验与业务逻辑在 service 层。
 */
@Tag(name = "媒资接口")
@RestController
@RequestMapping("/medias")
@RequiredArgsConstructor
public class MediaController {

    private final IMediaService mediaService;

    /** 上传文件/视频（multipart 表单，参数名 file），返回含访问 URL 的媒资信息 */
    @PostMapping("/upload")
    @Operation(summary = "上传文件/视频（multipart，参数名 file）")
    public R<MediaVO> upload(@RequestParam("file") MultipartFile file) {
        return R.ok(mediaService.upload(file));
    }

    /** 我的媒资分页（按当前用户过滤，可按类型筛选） */
    @GetMapping("/page")
    @Operation(summary = "我的媒资分页")
    public R<PageDTO<MediaVO>> queryMyPage(MediaPageQuery query) {
        return R.ok(mediaService.queryMyPage(query));
    }

    /** 媒资详情 */
    @GetMapping("/{id}")
    @Operation(summary = "媒资详情")
    public R<MediaVO> getDetail(@PathVariable("id") Long id) {
        return R.ok(mediaService.getDetail(id));
    }

    /** 删除媒资（仅本人，逻辑删除） */
    @DeleteMapping("/{id}")
    @Operation(summary = "删除媒资")
    public R<Void> delete(@PathVariable("id") Long id) {
        mediaService.delete(id);
        return R.ok();
    }
}
