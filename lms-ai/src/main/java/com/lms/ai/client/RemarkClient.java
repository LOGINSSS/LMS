package com.lms.ai.client;

import com.lms.common.domain.R;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Map;

/**
 * lms-remark 点赞服务 Feign 契约（spec §3.3：remark-agent 工具面）
 *
 * bizType：1 课程 / 2 笔记 / 3 问答；批量状态查询 bizIds 逗号分隔。
 */
@FeignClient(name = "lms-remark", contextId = "remarkClient")
public interface RemarkClient {

    @PostMapping("/likes/{bizType}/{bizId}")
    R<Object> toggleLike(@PathVariable("bizType") Integer bizType, @PathVariable("bizId") Long bizId);

    @GetMapping("/likes/count/{bizType}/{bizId}")
    R<Long> likeCount(@PathVariable("bizType") Integer bizType, @PathVariable("bizId") Long bizId);

    @GetMapping("/likes/status/{bizType}/{bizId}")
    R<Object> likeStatus(@PathVariable("bizType") Integer bizType, @PathVariable("bizId") Long bizId);

    @GetMapping("/likes/statuses/{bizType}")
    R<Map<Long, Boolean>> likeStatuses(@PathVariable("bizType") Integer bizType,
                                       @RequestParam("bizIds") String bizIds);
}
