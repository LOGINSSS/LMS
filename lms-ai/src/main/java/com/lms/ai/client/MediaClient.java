package com.lms.ai.client;

import com.lms.common.domain.R;
import com.lms.common.domain.dto.PageDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.multipart.MultipartFile;

/**
 * lms-media 媒资服务 Feign 契约（spec §3.3：media-agent 工具面）
 *
 * 上传 multipart 字段名 file；需要登录（user-info 头）由拦截器补。
 */
@FeignClient(name = "lms-media", contextId = "mediaClient")
public interface MediaClient {

    @PostMapping(value = "/medias/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    R<Object> upload(@RequestPart("file") MultipartFile file);

    @GetMapping("/medias/page")
    R<PageDTO<Object>> page(@RequestParam(value = "type", required = false) Integer type,
                            @RequestParam(value = "pageNo", required = false) Integer pageNo,
                            @RequestParam(value = "pageSize", required = false) Integer pageSize);

    @GetMapping("/medias/{id}")
    R<Object> getDetail(@PathVariable("id") Long id);

    @DeleteMapping("/medias/{id}")
    R<Void> delete(@PathVariable("id") Long id);
}
