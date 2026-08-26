package com.lms.search.search.service;

import com.lms.common.domain.dto.PageDTO;
import com.lms.search.search.domain.vo.CourseDocVO;

import java.util.List;

/**
 * 搜索业务服务
 *
 * 承载课程索引同步、ES 课程搜索、按兴趣推荐、兴趣标签管理。
 */
public interface ISearchService {

    /**
     * 同步课程数据到 ES 索引（全量重建：先清空再写入已发布课程）
     */
    void syncCourses();

    /**
     * ES 课程搜索（关键字匹配名称/简介，分类精确筛选）
     *
     * @param keyword  关键字（可选，空则查全部）
     * @param category 分类（可选）
     * @param pageNo   页码（从 1 起）
     * @param pageSize 每页大小
     * @return 搜索结果分页
     */
    PageDTO<CourseDocVO> search(String keyword, String category, Integer pageNo, Integer pageSize);

    /**
     * 按当前用户兴趣标签推荐课程
     *
     * @param size 返回条数（默认 10）
     * @return 推荐课程列表（无兴趣标签返回空）
     */
    List<CourseDocVO> recommend(Integer size);

    /**
     * 上报兴趣标签（同一标签权重累加，幂等）
     *
     * @param tag 兴趣标签（如课程分类）
     */
    void recordTag(String tag);

    /**
     * 我的兴趣标签（按权重降序）
     *
     * @return 标签字符串列表
     */
    List<String> myTags();
}
