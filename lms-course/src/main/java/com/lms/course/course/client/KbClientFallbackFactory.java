package com.lms.course.course.client;

import com.lms.common.domain.R;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Python RAG 自动建库/正文同步失败降级：记录日志，不阻断建课/发布主流程
 * （课程知识库可在后续手动补建，幂等 uk(courseId)）。
 */
@Slf4j
@Component
public class KbClientFallbackFactory implements FallbackFactory<KbClient> {

    @Override
    public KbClient create(Throwable cause) {
        return new KbClient() {
            @Override
            public R<Long> createKb(Map<String, Object> body) {
                log.warn("调用 RAG 服务自动建库失败 courseId={}: {}", body.get("courseId"), cause.getMessage());
                return R.fail("课程知识库创建失败");
            }

            @Override
            public R<Long> syncCourseText(Long courseId, Map<String, String> body) {
                log.warn("调用 RAG 服务正文同步失败 courseId={}: {}", courseId, cause.getMessage());
                return R.fail("课程正文同步失败");
            }

            @Override
            public R<Map<String, Object>> deleteCourse(Long courseId) {
                log.warn("调用 RAG 服务清理课程知识库失败 courseId={}: {}", courseId, cause.getMessage());
                return R.fail("课程知识库清理失败");
            }
        };
    }
}
