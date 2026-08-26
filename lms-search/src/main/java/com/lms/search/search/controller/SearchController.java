package com.lms.search.search.controller;

import com.lms.common.domain.R;
import com.lms.common.domain.dto.PageDTO;
import com.lms.search.search.domain.vo.CourseDocVO;
import com.lms.search.search.service.ISearchService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 课程搜索接口
 *
 * 职责：ES 课程搜索、按兴趣推荐、索引同步，只接收参数并调用服务。
 */
@Tag(name = "课程搜索接口")
@RestController
@RequestMapping("/search")
@RequiredArgsConstructor
public class SearchController {

    private final ISearchService searchService;

    /** 同步课程索引到 ES（管理端手动触发，全量重建） */
    @PostMapping("/sync")
    @Operation(summary = "同步课程索引到 ES")
    public R<Void> sync() {
        searchService.syncCourses();
        return R.ok();
    }

    /** ES 课程搜索（关键字匹配名称/简介，分类精确筛选） */
    @GetMapping("/courses")
    @Operation(summary = "课程搜索")
    public R<PageDTO<CourseDocVO>> search(@RequestParam(value = "keyword", required = false) String keyword,
                                          @RequestParam(value = "category", required = false) String category,
                                          @RequestParam(value = "pageNo", defaultValue = "1") Integer pageNo,
                                          @RequestParam(value = "pageSize", defaultValue = "10") Integer pageSize) {
        return R.ok(searchService.search(keyword, category, pageNo, pageSize));
    }

    /** 按当前用户兴趣标签推荐课程 */
    @GetMapping("/recommend")
    @Operation(summary = "按兴趣标签推荐课程")
    public R<List<CourseDocVO>> recommend(@RequestParam(value = "size", defaultValue = "10") Integer size) {
        return R.ok(searchService.recommend(size));
    }
}
