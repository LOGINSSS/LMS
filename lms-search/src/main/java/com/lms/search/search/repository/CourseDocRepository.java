package com.lms.search.search.repository;

import com.lms.search.search.domain.es.CourseDoc;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;

/**
 * 课程索引仓储
 *
 * 用途：对 ES course 索引的简单操作（全量重建用 deleteAll/saveAll，搜索走 ElasticsearchOperations）。
 */
public interface CourseDocRepository extends ElasticsearchRepository<CourseDoc, Long> {
}
