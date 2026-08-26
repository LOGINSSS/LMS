package com.lms.search.search.service.impl;

import cn.hutool.core.util.StrUtil;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lms.common.domain.dto.PageDTO;
import com.lms.common.exceptions.CommonException;
import com.lms.common.utils.AssertUtils;
import com.lms.common.utils.BeanUtils;
import com.lms.common.utils.CollUtils;
import com.lms.common.utils.UserContext;
import com.lms.search.search.client.CourseClient;
import com.lms.search.search.client.CourseCardDTO;
import com.lms.search.search.constants.SearchErrorInfo;
import com.lms.search.search.domain.es.CourseDoc;
import com.lms.search.search.domain.po.UserInterest;
import com.lms.search.search.domain.vo.CourseDocVO;
import com.lms.search.search.mapper.UserInterestMapper;
import com.lms.search.search.repository.CourseDocRepository;
import com.lms.search.search.service.ISearchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 搜索业务服务实现
 *
 * 数据边界：课程主数据在 lms-course，本服务维护 ES 索引副本 + 兴趣标签；
 * 索引同步为全量重建（练手简单可靠，生产可改增量/MQ 事件）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SearchServiceImpl implements ISearchService {

    /** 同步时每页拉取条数 */
    private static final int SYNC_PAGE_SIZE = 100;

    /** 推荐按兴趣取前几个标签 */
    private static final int RECOMMEND_TAG_LIMIT = 3;

    private final CourseClient courseClient;
    private final CourseDocRepository courseDocRepository;
    private final ElasticsearchOperations operations;
    private final UserInterestMapper interestMapper;

    @Override
    public void syncCourses() {
        try {
            //1. 循环分页拉取课程：从 lms-course 全量拉已发布课程
            List<CourseDoc> docs = new java.util.ArrayList<>();
            int pageNo = 1;
            while (true) {
                var resp = courseClient.queryPage(pageNo, SYNC_PAGE_SIZE);
                if (resp == null || resp.getData() == null || CollUtils.isEmpty(resp.getData().getList())) {
                    break;
                }
                //2. 过滤已发布课程并转索引文档
                resp.getData().getList().stream()
                        .filter(dto -> dto.getStatus() != null && dto.getStatus() == 1)
                        .map(this::toDoc)
                        .forEach(docs::add);
                //3. 不足一页说明拉完了
                if (resp.getData().getList().size() < SYNC_PAGE_SIZE) {
                    break;
                }
                pageNo++;
            }
            //4. 重建索引：删除旧索引（含旧 mapping）再按实体 mapping 重建，保证 keyword 字段类型生效
            org.springframework.data.elasticsearch.core.IndexOperations indexOps = operations.indexOps(CourseDoc.class);
            indexOps.delete();
            indexOps.create();
            if (CollUtils.isNotEmpty(docs)) {
                courseDocRepository.saveAll(docs);
            }
            log.info("课程索引同步完成，共 {} 条", docs.size());
        } catch (Exception e) {
            log.error("课程索引同步失败", e);
            throw new CommonException(SearchErrorInfo.SYNC_FAILED);
        }
    }

    @Override
    public PageDTO<CourseDocVO> search(String keyword, String category, Integer pageNo, Integer pageSize) {
        //1. 参数兜底：页码从 1 起，每页 1-100
        int safePage = pageNo == null || pageNo < 1 ? 1 : pageNo;
        int safeSize = pageSize == null || pageSize < 1 ? 10 : Math.min(pageSize, 100);
        //2. 组装 ES 查询：关键字 multiMatch 匹配名称/简介，分类 term 精确筛选
        Query query = new Query.Builder().bool(b -> {
            if (StrUtil.isNotBlank(keyword)) {
                b.must(m -> m.multiMatch(mm -> mm.query(keyword.trim()).fields("name", "intro")));
            }
            if (StrUtil.isNotBlank(category)) {
                // 精确匹配用 keyword 子字段：category 映射为 text+keyword，term 需查 category.keyword
                b.filter(f -> f.term(t -> t.field("category.keyword").value(category.trim())));
            }
            return b;
        }).build();
        //3. 执行搜索并转出参
        SearchHits<CourseDoc> hits = operations.search(
                NativeQuery.builder().withQuery(query).withPageable(PageRequest.of(safePage - 1, safeSize)).build(),
                CourseDoc.class);
        long total = hits.getTotalHits();
        List<CourseDocVO> vos = hits.getSearchHits().stream()
                .map(SearchHit::getContent)
                .map(doc -> BeanUtils.copyBean(doc, CourseDocVO.class))
                .collect(Collectors.toList());
        return PageDTO.of(total, vos);
    }

    @Override
    public List<CourseDocVO> recommend(Integer size) {
        //1. 取当前用户兴趣标签（权重降序前 3 个）
        Long userId = UserContext.getUser();
        if (userId == null) {
            return Collections.emptyList();
        }
        List<UserInterest> interests = interestMapper.selectList(new LambdaQueryWrapper<UserInterest>()
                .eq(UserInterest::getUserId, userId)
                .orderByDesc(UserInterest::getWeight)
                .last("LIMIT " + RECOMMEND_TAG_LIMIT));
        if (CollUtils.isEmpty(interests)) {
            return Collections.emptyList();
        }
        //2. 按第一个兴趣标签匹配课程分类（term 用 keyword 子字段精确匹配）
        String tag = interests.get(0).getTag();
        Query query = new Query.Builder()
                .bool(b -> b.filter(f -> f.term(t -> t.field("category.keyword").value(tag))))
                .build();
        int safeSize = size == null || size < 1 ? 10 : Math.min(size, 50);
        SearchHits<CourseDoc> hits = operations.search(
                NativeQuery.builder().withQuery(query).withPageable(PageRequest.of(0, safeSize)).build(),
                CourseDoc.class);
        //3. 转出参返回
        return hits.getSearchHits().stream()
                .map(SearchHit::getContent)
                .map(doc -> BeanUtils.copyBean(doc, CourseDocVO.class))
                .collect(Collectors.toList());
    }

    @Override
    public void recordTag(String tag) {
        //1. 登录校验：兴趣标签归属当前用户
        Long userId = UserContext.getUser();
        AssertUtils.isNotNull(userId, "请先登录");
        //2. 标签合法性校验
        if (StrUtil.isBlank(tag) || tag.trim().length() > 50) {
            throw new CommonException(SearchErrorInfo.TAG_INVALID);
        }
        String safeTag = tag.trim();
        //3. 按 uk_user_tag 唯一键查：存在则权重 +1，不存在则插入
        //   【保障机制】幂等：uk_user_tag 唯一索引 + DuplicateKeyException 并发兜底
        UserInterest existed = interestMapper.selectOne(new LambdaQueryWrapper<UserInterest>()
                .eq(UserInterest::getUserId, userId)
                .eq(UserInterest::getTag, safeTag));
        if (existed != null) {
            existed.setWeight(existed.getWeight() + 1);
            interestMapper.updateById(existed);
            return;
        }
        UserInterest interest = new UserInterest();
        interest.setUserId(userId);
        interest.setTag(safeTag);
        interest.setWeight(1);
        try {
            interestMapper.insert(interest);
        } catch (DuplicateKeyException e) {
            // 并发兜底：撞唯一键说明已存在，重查后权重 +1
            UserInterest again = interestMapper.selectOne(new LambdaQueryWrapper<UserInterest>()
                    .eq(UserInterest::getUserId, userId)
                    .eq(UserInterest::getTag, safeTag));
            if (again != null) {
                again.setWeight(again.getWeight() + 1);
                interestMapper.updateById(again);
            }
        }
    }

    @Override
    public List<String> myTags() {
        //1. 登录校验
        Long userId = UserContext.getUser();
        AssertUtils.isNotNull(userId, "请先登录");
        //2. 查询按权重降序，只返回标签
        List<UserInterest> interests = interestMapper.selectList(new LambdaQueryWrapper<UserInterest>()
                .eq(UserInterest::getUserId, userId)
                .orderByDesc(UserInterest::getWeight));
        return interests.stream().map(UserInterest::getTag).collect(Collectors.toList());
    }

    /**
     * 课程卡片 DTO 转 ES 索引文档
     */
    private CourseDoc toDoc(CourseCardDTO dto) {
        CourseDoc doc = new CourseDoc();
        doc.setId(dto.getId());
        doc.setName(dto.getName());
        doc.setIntro(dto.getIntro());
        doc.setCategory(dto.getCategory());
        doc.setTeacherName(dto.getTeacherName());
        doc.setCover(dto.getCover());
        return doc;
    }
}
