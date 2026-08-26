package com.lms.search.search.controller;

import com.lms.common.domain.R;
import com.lms.search.search.domain.dto.TagFormDTO;
import com.lms.search.search.service.ISearchService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 兴趣标签接口
 *
 * 职责：兴趣标签上报与查询（推荐数据源），只接收参数并调用服务。
 */
@Tag(name = "兴趣标签接口")
@RestController
@RequestMapping("/interests")
@RequiredArgsConstructor
public class InterestController {

    private final ISearchService searchService;

    /** 上报兴趣标签（选课/浏览等行为时调用，同一标签权重累加） */
    @PostMapping("/record")
    @Operation(summary = "上报兴趣标签")
    public R<Void> record(@RequestBody @Valid TagFormDTO dto) {
        searchService.recordTag(dto.getTag());
        return R.ok();
    }

    /** 我的兴趣标签（按权重降序） */
    @GetMapping
    @Operation(summary = "我的兴趣标签")
    public R<List<String>> myTags() {
        return R.ok(searchService.myTags());
    }
}
