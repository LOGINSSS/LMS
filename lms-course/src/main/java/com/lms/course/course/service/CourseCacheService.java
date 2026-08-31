package com.lms.course.course.service;

import cn.hutool.json.JSONUtil;
import com.lms.course.course.config.CourseCacheProperties;
import com.lms.course.course.domain.dto.CourseCardVO;
import com.lms.course.course.domain.vo.CatalogNodeVO;
import com.lms.course.course.domain.vo.ChapterVO;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;

/**
 * 课程内容缓存服务（spec 0.2 §5 Redis 缓存体系）
 *
 * Cache-Aside + 主动失效：读走 Redis，写操作后由调用方主动删除 key。
 * 缓存项：课程详情 / 章节大纲（左栏）/ 章节正文（右栏）。
 */
@Service
@RequiredArgsConstructor
public class CourseCacheService {

    private final StringRedisTemplate redisTemplate;
    private final CourseCacheProperties properties;

    // ---------- 课程详情 ----------

    public CourseCardVO getCourseDetail(Long courseId, java.util.function.Supplier<CourseCardVO> loader) {
        String key = properties.courseDetailKey(courseId);
        String cached = redisTemplate.opsForValue().get(key);
        if (cached != null) {
            return JSONUtil.toBean(cached, CourseCardVO.class);
        }
        CourseCardVO vo = loader.get();
        if (vo != null) {
            redisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(vo),
                    Duration.ofMinutes(properties.getCourseDetailTtlMinutes()));
        }
        return vo;
    }

    public void evictCourseDetail(Long courseId) {
        redisTemplate.delete(properties.courseDetailKey(courseId));
    }

    // ---------- 章节大纲 ----------

    @SuppressWarnings("unchecked")
    public List<CatalogNodeVO> getCatalog(Long courseId, java.util.function.Supplier<List<CatalogNodeVO>> loader) {
        String key = properties.catalogKey(courseId);
        String cached = redisTemplate.opsForValue().get(key);
        if (cached != null) {
            return JSONUtil.toList(cached, CatalogNodeVO.class);
        }
        List<CatalogNodeVO> list = loader.get();
        if (list != null) {
            redisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(list),
                    Duration.ofMinutes(properties.getCatalogTtlMinutes()));
        }
        return list;
    }

    public void evictCatalog(Long courseId) {
        redisTemplate.delete(properties.catalogKey(courseId));
    }

    // ---------- 章节正文 ----------

    public ChapterVO getChapter(Long chapterId, java.util.function.Supplier<ChapterVO> loader) {
        String key = properties.chapterKey(chapterId);
        String cached = redisTemplate.opsForValue().get(key);
        if (cached != null) {
            return JSONUtil.toBean(cached, ChapterVO.class);
        }
        ChapterVO vo = loader.get();
        if (vo != null) {
            redisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(vo),
                    Duration.ofMinutes(properties.getChapterTtlMinutes()));
        }
        return vo;
    }

    public void evictChapter(Long chapterId) {
        redisTemplate.delete(properties.chapterKey(chapterId));
    }
}
