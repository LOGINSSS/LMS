package com.lms.ai.client;

import com.lms.common.domain.R;
import com.lms.common.domain.dto.PageDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.Map;

/**
 * lms-search 搜索服务 Feign 契约（spec §3.3：search-agent 工具面）
 *
 * TagFormDTO{tag}（兴趣标签上报，必填 @NotBlank）。
 */
@FeignClient(name = "lms-search", contextId = "searchClient")
public interface SearchClient {

    @GetMapping("/search/courses")
    R<PageDTO<Object>> courses(@RequestParam(value = "keyword", required = false) String keyword,
                               @RequestParam(value = "category", required = false) String category,
                               @RequestParam(value = "pageNo", required = false) Integer pageNo,
                               @RequestParam(value = "pageSize", required = false) Integer pageSize);

    @GetMapping("/search/recommend")
    R<List<Object>> recommend(@RequestParam(value = "size", required = false) Integer size);

    @PostMapping("/interests/record")
    R<Void> recordInterest(@RequestBody Map<String, Object> body);

    @GetMapping("/interests")
    R<List<String>> myInterests();
}
