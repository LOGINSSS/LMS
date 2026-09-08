package com.lms.course.course.service;

import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lms.common.enums.UserType;
import com.lms.common.exceptions.CommonException;
import com.lms.common.exceptions.ForbiddenException;
import com.lms.common.utils.UserContext;
import com.lms.course.course.domain.po.Course;
import com.lms.course.course.domain.po.CourseCategory;
import com.lms.course.course.enums.CourseStatus;
import com.lms.course.course.mapper.CourseCategoryMapper;
import com.lms.course.course.mapper.CourseMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 课程分类标签服务（全局共享 + Redis 缓存）
 *
 * 业务规则：
 * - 分类表 course_category 是唯一来源；分类列表经 Redis 缓存（Cache-Aside），
 *   新增分类后主动失效缓存，保证「新标签教师们都可见」且不穿透 DB。
 * - 空表时按默认分类种子初始化（微服务/前端/数据库/AI）。
 * - 新增仅限教师；分类名全局唯一（重名返回业务错误，并发由 uk_name 兜底）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CategoryService {

    private final CourseCategoryMapper categoryMapper;
    private final CourseMapper courseMapper;
    private final StringRedisTemplate redisTemplate;

    /** 分类列表缓存 key（存 JSON 数组：名称列表） */
    private static final String CACHE_KEY = "lms:course:categories";
    private static final Duration CACHE_TTL = Duration.ofMinutes(30);

    /**
     * 分类 → 可见课程 id 的 SET 索引前缀（无 TTL、永不过期；删课/下架不主动删除，
     * 广场读取时按课程详情过滤失效 id）。key: lms:course:cat:{分类名} → {courseId}
     */
    private static final String CAT_SET_PREFIX = "lms:course:cat:";

    private String catSetKey(String category) {
        return CAT_SET_PREFIX + category;
    }

    /** 默认分类种子（空表初始化） */
    private static final String[] DEFAULT_CATEGORIES = {"微服务", "前端", "数据库", "AI"};

    /**
     * 分类名称列表（缓存优先；空表先种子化再返回；只读接口，学生也可用）
     */
    public List<String> list() {
        String cached = redisTemplate.opsForValue().get(CACHE_KEY);
        if (cached != null) {
            return JSONUtil.toList(cached, String.class);
        }
        List<String> names = loadFromDb();
        if (names.isEmpty()) {
            seedDefaults();
            names = loadFromDb();
        }
        redisTemplate.opsForValue().set(CACHE_KEY, JSONUtil.toJsonStr(names), CACHE_TTL);
        return names;
    }

    /**
     * 新增分类（教师）：重名报业务错误；成功后失效缓存
     *
     * @param name 分类名称
     */
    public void add(String name) {
        Integer userType = UserContext.getUserType();
        Long userId = UserContext.getUser();
        if (userId == null || userType == null || userType != UserType.TEACHER.getValue()) {
            throw new ForbiddenException("仅教师可新增分类");
        }
        if (name == null || name.isBlank()) {
            throw new CommonException("分类名称不能为空");
        }
        String trimmed = name.trim();
        Long count = categoryMapper.selectCount(new LambdaQueryWrapper<CourseCategory>()
                .eq(CourseCategory::getName, trimmed));
        if (count != null && count > 0) {
            throw new CommonException("分类已存在：" + trimmed);
        }
        CourseCategory category = new CourseCategory();
        category.setName(trimmed);
        category.setCreateBy(userId);
        try {
            categoryMapper.insert(category);
        } catch (DuplicateKeyException e) {
            // 并发兜底：撞 uk_name 唯一索引
            throw new CommonException("分类已存在：" + trimmed);
        }
        // 【缓存失效】新增后删除缓存，下次读取回源并回填，全端教师即时可见
        redisTemplate.delete(CACHE_KEY);
        log.info("新增课程分类 name={} by teacherId={}", trimmed, userId);
    }

    // ================= 分类 → 可见课程 id 的 SET 索引（广场筛选用） =================

    /** 可见状态（广场展示）：抢课中(2) / 进行中(3) */
    private static final List<Integer> VISIBLE_STATUS = List.of(
            CourseStatus.GRABBING.getValue(), CourseStatus.ONGOING.getValue());

    /**
     * 单分类 set 懒构建：key 不存在时从 DB 回填该分类当前可见课程 id（幂等，仅建缺失的）。
     * 不设 TTL：key 一旦建成长期驻留；新增可见课程由 indexVisible/resyncVisible 增量补入。
     */
    private void ensureCatSet(String category) {
        String key = catSetKey(category);
        Boolean exists = redisTemplate.hasKey(key);
        if (Boolean.TRUE.equals(exists)) {
            return;
        }
        List<Long> ids = courseMapper.selectList(new LambdaQueryWrapper<Course>()
                        .eq(Course::getCategory, category)
                        .in(Course::getStatus, VISIBLE_STATUS))
                .stream().map(Course::getId).collect(Collectors.toList());
        if (!ids.isEmpty()) {
            redisTemplate.opsForSet().add(key,
                    ids.stream().map(String::valueOf).toArray(String[]::new));
        }
    }

    /** 课程进入可见状态 / 可见状态下改分类：把课程 id 补入新分类 set */
    public void indexVisible(Long courseId, String category) {
        if (courseId == null || category == null || category.isBlank()) {
            return;
        }
        redisTemplate.opsForSet().add(catSetKey(category), String.valueOf(courseId));
    }

    /** 课程删除 / 下架 / 离开可见状态 / 改分类：从原分类 set 移除，保持集合=当前可见课程 */
    public void removeVisible(Long courseId, String category) {
        if (courseId == null || category == null || category.isBlank()) {
            return;
        }
        redisTemplate.opsForSet().remove(catSetKey(category), String.valueOf(courseId));
    }

    /** 全量对账：把当前所有可见课程的 id 按其分类补入对应 set（定时流转后自愈新增课程） */
    public void resyncVisible() {
        List<Course> visible = courseMapper.selectList(new LambdaQueryWrapper<Course>()
                .in(Course::getStatus, VISIBLE_STATUS)
                .isNotNull(Course::getCategory));
        java.util.Map<String, List<String>> byCat = new java.util.HashMap<>();
        for (Course c : visible) {
            if (c.getCategory() == null || c.getCategory().isBlank()) {
                continue;
            }
            byCat.computeIfAbsent(c.getCategory(), k -> new ArrayList<>()).add(String.valueOf(c.getId()));
        }
        byCat.forEach((cat, ids) -> redisTemplate.opsForSet().add(catSetKey(cat), ids.toArray(String[]::new)));
    }

    /** 多选分类（逗号分隔，可空=全部）：各分类 set 并集 → 课程 id 列表（倒序，供分页） */
    public List<Long> unionVisibleIds(String categoriesCsv) {
        Set<String> cats = new HashSet<>();
        if (categoriesCsv != null && !categoriesCsv.isBlank()) {
            for (String c : categoriesCsv.split(",")) {
                if (!c.isBlank()) {
                    cats.add(c.trim());
                }
            }
        }
        List<String> keys;
        if (cats.isEmpty()) {
            // 全部分类：取可见课程中出现过的全部分类做并集（自愈补齐新分类的 set）
            cats = courseMapper.selectList(new LambdaQueryWrapper<Course>()
                            .in(Course::getStatus, VISIBLE_STATUS)
                            .isNotNull(Course::getCategory))
                    .stream().map(Course::getCategory)
                    .filter(c -> c != null && !c.isBlank())
                    .collect(Collectors.toSet());
            if (cats.isEmpty()) {
                return List.of();
            }
        }
        keys = new ArrayList<>();
        for (String cat : cats) {
            ensureCatSet(cat);
            keys.add(catSetKey(cat));
        }
        Set<String> members = redisTemplate.opsForSet().union(keys.get(0),
                keys.subList(1, keys.size()));
        return members.stream().map(Long::valueOf).sorted(java.util.Comparator.reverseOrder())
                .collect(Collectors.toList());
    }

    private List<String> loadFromDb() {
        return categoryMapper.selectList(new LambdaQueryWrapper<CourseCategory>()
                        .orderByAsc(CourseCategory::getId))
                .stream()
                .map(CourseCategory::getName)
                .filter(n -> n != null && !n.isBlank())
                .collect(Collectors.toList());
    }

    /** 空表种子初始化（默认分类；并发下撞唯一键忽略） */
    private void seedDefaults() {
        for (String name : DEFAULT_CATEGORIES) {
            CourseCategory c = new CourseCategory();
            c.setName(name);
            try {
                categoryMapper.insert(c);
            } catch (DuplicateKeyException e) {
                log.debug("默认分类已存在，跳过：{}", name);
            }
        }
    }
}
