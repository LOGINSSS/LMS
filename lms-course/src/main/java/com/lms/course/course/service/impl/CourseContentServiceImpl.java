package com.lms.course.course.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lms.common.exceptions.CommonException;
import com.lms.common.exceptions.ForbiddenException;
import com.lms.common.utils.AssertUtils;
import com.lms.common.utils.BeanUtils;
import com.lms.common.utils.CollUtils;
import com.lms.common.utils.UserContext;
import com.lms.course.course.constants.CourseErrorInfo;
import com.lms.course.course.domain.dto.CatalogFormDTO;
import com.lms.course.course.domain.dto.ChapterFormDTO;
import com.lms.course.course.domain.po.Course;
import com.lms.course.course.domain.po.CourseCatalog;
import com.lms.course.course.domain.po.CourseChapter;
import com.lms.course.course.domain.vo.CatalogNodeVO;
import com.lms.course.course.domain.vo.ChapterVO;
import com.lms.course.course.mapper.CourseCatalogMapper;
import com.lms.course.course.mapper.CourseChapterMapper;
import com.lms.course.course.mapper.CourseMapper;
import com.lms.course.course.service.CourseCacheService;
import com.lms.course.course.service.ICourseContentService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 课程内容业务服务实现
 *
 * 事务边界：目录增删改/正文保存均为单事务；删除目录递归删节与正文。
 * 权限规则：写操作仅限课程归属教师本人（与建课/改课一致）；
 * 读操作（大纲/正文）登录即可，正文是否需选课拥有由调用方控制。
 */
@Service
@RequiredArgsConstructor
public class CourseContentServiceImpl implements ICourseContentService {

    private final CourseCatalogMapper catalogMapper;
    private final CourseChapterMapper chapterMapper;
    private final CourseMapper courseMapper;
    private final CourseCacheService cacheService;

    @Override
    public List<CatalogNodeVO> getCatalog(Long courseId) {
        //1. 走缓存（Cache-Aside，spec 0.2 §5.1）
        return cacheService.getCatalog(courseId, () -> {
            //1.1 查课程目录全部节点（含章+节），按 sort 升序
            List<CourseCatalog> nodes = catalogMapper.selectList(new LambdaQueryWrapper<CourseCatalog>()
                    .eq(CourseCatalog::getCourseId, courseId)
                    .orderByAsc(CourseCatalog::getSort));
            if (CollUtils.isEmpty(nodes)) {
                return new ArrayList<>();
            }
            //1.2 分章（parentId=0）与节，组装树
            Map<Long, List<CourseCatalog>> byParent = nodes.stream()
                    .collect(Collectors.groupingBy(n -> n.getParentId() == null ? 0L : n.getParentId()));
            List<CatalogNodeVO> roots = new ArrayList<>();
            for (CourseCatalog chapter : byParent.getOrDefault(0L, new ArrayList<>())) {
                CatalogNodeVO vo = toNode(chapter);
                // 节按 sort 升序
                List<CourseCatalog> sections = byParent.getOrDefault(chapter.getId(), new ArrayList<>());
                sections.sort(Comparator.comparing(CourseCatalog::getSort, Comparator.nullsLast(Comparator.naturalOrder())));
                vo.setChildren(sections.stream().map(this::toNode).collect(Collectors.toList()));
                roots.add(vo);
            }
            return roots;
        });
    }

    @Override
    public ChapterVO getChapter(Long catalogId) {
        //1. 走缓存（Cache-Aside，spec 0.2 §5.1）
        return cacheService.getChapter(catalogId, () -> {
            //1.1 目录节点存在性校验，取名称
            CourseCatalog catalog = getCatalogEntity(catalogId);
            //1.2 查正文（可能尚未编写，返回空正文）
            CourseChapter chapter = chapterMapper.selectOne(new LambdaQueryWrapper<CourseChapter>()
                    .eq(CourseChapter::getCatalogId, catalogId));
            ChapterVO vo = new ChapterVO();
            vo.setCatalogId(catalogId);
            vo.setName(catalog.getName());
            if (chapter != null) {
                vo.setContentMd(chapter.getContentMd());
                vo.setWordCount(chapter.getWordCount());
            }
            return vo;
        });
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long addCatalog(Long courseId, CatalogFormDTO dto) {
        //1. 权限校验：仅归属教师可维护内容
        assertOwner(courseId);
        //2. 层级校验：节必须挂在章下（父节点必须存在且为章）
        validateParent(courseId, dto);
        //3. 落库
        CourseCatalog node = BeanUtils.copyBean(dto, CourseCatalog.class);
        node.setCourseId(courseId);
        if (node.getSort() == null) {
            node.setSort(0);
        }
        catalogMapper.insert(node);
        //4. 失效课程大纲缓存
        cacheService.evictCatalog(courseId);
        return node.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateCatalog(Long catalogId, CatalogFormDTO dto) {
        //1. 节点存在性 + 归属校验
        CourseCatalog node = getOwnCatalog(catalogId);
        //2. 父节点校验（同课程内，防止挂到别的课程）
        validateParent(node.getCourseId(), dto);
        //3. 更新
        CourseCatalog update = BeanUtils.copyBean(dto, CourseCatalog.class);
        update.setId(catalogId);
        catalogMapper.updateById(update);
        //4. 失效大纲缓存（课程维度）
        cacheService.evictCatalog(node.getCourseId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteCatalog(Long catalogId) {
        //1. 节点存在性 + 归属校验
        CourseCatalog node = getOwnCatalog(catalogId);
        //2. 递归收集自身 + 子节点 id
        List<Long> ids = new ArrayList<>();
        collectIds(catalogId, ids);
        //3. 删除目录节点（逻辑删）
        catalogMapper.deleteBatchIds(ids);
        //4. 删除对应正文（逻辑删）
        chapterMapper.delete(new LambdaQueryWrapper<CourseChapter>().in(CourseChapter::getCatalogId, ids));
        //5. 失效缓存：大纲（课程维度）+ 各章节正文
        cacheService.evictCatalog(node.getCourseId());
        ids.forEach(cacheService::evictChapter);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void saveChapter(Long catalogId, ChapterFormDTO dto) {
        //1. 节点存在性 + 归属校验
        CourseCatalog node = getOwnCatalog(catalogId);
        //2. 幂等：uk_catalog 存在则更新，不存在则插入
        CourseChapter chapter = chapterMapper.selectOne(new LambdaQueryWrapper<CourseChapter>()
                .eq(CourseChapter::getCatalogId, catalogId));
        String content = dto.getContentMd();
        int wordCount = content.replaceAll("\\s", "").length();
        if (chapter != null) {
            chapter.setContentMd(content);
            chapter.setWordCount(wordCount);
            chapterMapper.updateById(chapter);
        } else {
            CourseChapter insert = new CourseChapter();
            insert.setCatalogId(catalogId);
            insert.setCourseId(node.getCourseId());
            insert.setContentMd(content);
            insert.setWordCount(wordCount);
            chapterMapper.insert(insert);
        }
        //3. 失效章节正文缓存
        cacheService.evictChapter(catalogId);
    }

    // ---------- 私有工具 ----------

    /**
     * 目录节点 → 大纲 VO（不含 children 填充）
     */
    private CatalogNodeVO toNode(CourseCatalog node) {
        CatalogNodeVO vo = new CatalogNodeVO();
        vo.setId(node.getId());
        vo.setName(node.getName());
        vo.setLevel(node.getLevel());
        return vo;
    }

    /**
     * 校验父节点：level=2（节）必须挂在一个存在的章（level=1）下；章必须挂根（parentId=0）
     */
    private void validateParent(Long courseId, CatalogFormDTO dto) {
        Long parentId = dto.getParentId() == null ? 0L : dto.getParentId();
        int level = dto.getLevel() == null ? 1 : dto.getLevel();
        if (level == 1) {
            if (parentId != 0L) {
                throw new CommonException(CourseErrorInfo.CATALOG_PARENT_INVALID);
            }
        } else {
            if (parentId == null || parentId == 0L) {
                throw new CommonException(CourseErrorInfo.CATALOG_PARENT_INVALID);
            }
            CourseCatalog parent = catalogMapper.selectById(parentId);
            AssertUtils.notNull(parent, CourseErrorInfo.CATALOG_NOT_FOUND.getMsg());
            if (!parent.getCourseId().equals(courseId) || parent.getLevel() == null || parent.getLevel() != 1) {
                throw new CommonException(CourseErrorInfo.CATALOG_PARENT_INVALID);
            }
        }
    }

    /**
     * 递归收集节点 id（含子节点）
     */
    private void collectIds(Long parentId, List<Long> ids) {
        ids.add(parentId);
        List<CourseCatalog> children = catalogMapper.selectList(new LambdaQueryWrapper<CourseCatalog>()
                .eq(CourseCatalog::getParentId, parentId));
        for (CourseCatalog child : children) {
            collectIds(child.getId(), ids);
        }
    }

    /**
     * 校验当前用户为课程归属教师，返回课程（非本人抛 Forbidden）
     */
    private void assertOwner(Long courseId) {
        Course course = courseMapper.selectById(courseId);
        AssertUtils.notNull(course, CourseErrorInfo.COURSE_NOT_FOUND.getMsg());
        Long userId = UserContext.getUser();
        if (userId == null || !userId.equals(course.getTeacherId())) {
            throw new ForbiddenException("只能维护自己创建的课程内容");
        }
    }

    /**
     * 查询目录节点（不存在抛业务异常）
     */
    private CourseCatalog getCatalogEntity(Long catalogId) {
        CourseCatalog node = catalogMapper.selectById(catalogId);
        AssertUtils.notNull(node, CourseErrorInfo.CATALOG_NOT_FOUND.getMsg());
        return node;
    }

    /**
     * 查询归属教师的目录节点（不存在或非本人抛异常）
     */
    private CourseCatalog getOwnCatalog(Long catalogId) {
        CourseCatalog node = getCatalogEntity(catalogId);
        Course course = courseMapper.selectById(node.getCourseId());
        AssertUtils.notNull(course, CourseErrorInfo.COURSE_NOT_FOUND.getMsg());
        Long userId = UserContext.getUser();
        if (userId == null || !userId.equals(course.getTeacherId())) {
            throw new ForbiddenException("只能维护自己创建的课程内容");
        }
        return node;
    }
}
