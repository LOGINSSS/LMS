package com.lms.remark.remark.controller;

import com.lms.common.domain.R;
import com.lms.remark.remark.domain.vo.LikeStatusVO;
import com.lms.remark.remark.service.ILikeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 点赞接口（跨课程/笔记/问答对象）
 *
 * 职责：接收前端点赞请求、参数解析与转换，调用服务并返回结果，不承载业务逻辑。
 */
@Tag(name = "点赞接口")
@RestController
@RequestMapping("/likes")
@RequiredArgsConstructor
public class LikeController {

    private final ILikeService likeService;

    /** 点赞/取消点赞（切换，幂等）：重复调用在已赞/未赞之间翻转 */
    @PostMapping("/{bizType}/{bizId}")
    @Operation(summary = "点赞/取消点赞（切换，幂等）")
    public R<LikeStatusVO> toggle(@PathVariable("bizType") Integer bizType,
                                  @PathVariable("bizId") Long bizId) {
        return R.ok(likeService.toggle(bizType, bizId));
    }

    /** 点赞总数 */
    @GetMapping("/count/{bizType}/{bizId}")
    @Operation(summary = "点赞总数")
    public R<Long> count(@PathVariable("bizType") Integer bizType,
                         @PathVariable("bizId") Long bizId) {
        return R.ok(likeService.count(bizType, bizId));
    }

    /** 当前用户点赞状态 */
    @GetMapping("/status/{bizType}/{bizId}")
    @Operation(summary = "当前用户点赞状态")
    public R<LikeStatusVO> status(@PathVariable("bizType") Integer bizType,
                                  @PathVariable("bizId") Long bizId) {
        return R.ok(likeService.status(bizType, bizId));
    }

    /** 批量点赞状态（列表页用，bizIds 逗号分隔） */
    @GetMapping("/statuses/{bizType}")
    @Operation(summary = "批量点赞状态（列表页用，bizIds 逗号分隔）")
    public R<Map<Long, Boolean>> statusBatch(@PathVariable("bizType") Integer bizType,
                                             @RequestParam("bizIds") String bizIds) {
        //1. 逗号分隔转 id 集合（去空白），供服务层一次查询
        List<Long> idList = Arrays.stream(bizIds.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(Long::valueOf)
                .collect(Collectors.toList());
        return R.ok(likeService.statusBatch(bizType, idList));
    }
}
