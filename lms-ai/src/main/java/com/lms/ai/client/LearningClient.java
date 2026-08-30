package com.lms.ai.client;

import com.lms.common.domain.R;
import com.lms.common.domain.dto.PageDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.Map;

/**
 * lms-learning 学习过程服务 Feign 契约（spec §3.3：learning-agent 工具面）
 *
 * 请求体字段：LearningRecordFormDTO{lessonId,progress}、NoteFormDTO{courseId,lessonId,content}、
 * QaQuestionFormDTO{courseId,title,content}、AnswerFormDTO{content}、LessonFormDTO{courseId,name,mediaId,sort}。
 */
@FeignClient(name = "lms-learning", contextId = "learningClient")
public interface LearningClient {

    @GetMapping("/lessons")
    R<List<Object>> lessonList(@RequestParam("courseId") Long courseId);

    @GetMapping("/lessons/{id}")
    R<Object> lessonDetail(@PathVariable("id") Long id);

    @PostMapping("/lessons/learn/records")
    R<Void> reportProgress(@RequestBody Map<String, Object> body);

    @GetMapping("/lessons/learn/progress")
    R<Integer> courseProgress(@RequestParam("courseId") Long courseId);

    @PostMapping("/lessons/admin/lessons")
    R<Long> addLesson(@RequestBody Map<String, Object> body);

    @GetMapping("/learn/stats/my")
    R<Object> myStats();

    @PostMapping("/notes")
    R<Long> addNote(@RequestBody Map<String, Object> body);

    @GetMapping("/notes/page")
    R<PageDTO<Object>> notePage(@RequestParam(value = "courseId", required = false) Long courseId,
                                @RequestParam(value = "pageNo", required = false) Integer pageNo,
                                @RequestParam(value = "pageSize", required = false) Integer pageSize);

    @PutMapping("/notes/{id}")
    R<Void> updateNote(@PathVariable("id") Long id, @RequestBody Map<String, Object> body);

    @DeleteMapping("/notes/{id}")
    R<Void> deleteNote(@PathVariable("id") Long id);

    @PostMapping("/qa/questions")
    R<Long> askQuestion(@RequestBody Map<String, Object> body);

    @GetMapping("/qa/questions/page")
    R<PageDTO<Object>> qaPage(@RequestParam(value = "courseId", required = false) Long courseId,
                              @RequestParam(value = "pageNo", required = false) Integer pageNo,
                              @RequestParam(value = "pageSize", required = false) Integer pageSize);

    @PostMapping("/qa/questions/{id}/answers")
    R<Long> answerQuestion(@PathVariable("id") Long id, @RequestBody Map<String, Object> body);

    @PostMapping("/points/sign-in")
    R<Void> signIn();

    @GetMapping("/points/records")
    R<PageDTO<Object>> pointsRecords(@RequestParam(value = "pageNo", required = false) Integer pageNo,
                                     @RequestParam(value = "pageSize", required = false) Integer pageSize);

    @GetMapping("/points/board")
    R<List<Object>> pointsBoard(@RequestParam(value = "size", required = false) Integer size);
}
