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
 * lms-exam 考试题库服务 Feign 契约（spec §3.3：exam-agent 工具面）
 *
 * 请求体字段与 QuestionFormDTO 一致：name(必填)/type(1单选2多选3判断)/category/
 * difficulty(1易2中3难)/analysis/answer(JSON)；绑定业务走 POST /admin/questions/{id}/biz?bizId=。
 */
@FeignClient(name = "lms-exam", contextId = "examClient")
public interface ExamClient {

    @PostMapping("/admin/questions")
    R<Long> saveQuestion(@RequestBody Map<String, Object> body);

    @PutMapping("/admin/questions/{id}")
    R<Void> updateQuestion(@PathVariable("id") Long id, @RequestBody Map<String, Object> body);

    @DeleteMapping("/admin/questions/{id}")
    R<Void> deleteQuestion(@PathVariable("id") Long id);

    @GetMapping("/admin/questions/{id}")
    R<Object> getQuestion(@PathVariable("id") Long id);

    @GetMapping("/admin/questions/page")
    R<PageDTO<Object>> queryQuestionPage(@RequestParam(value = "type", required = false) Integer type,
                                         @RequestParam(value = "category", required = false) String category,
                                         @RequestParam(value = "difficulty", required = false) Integer difficulty,
                                         @RequestParam(value = "pageNo", required = false) Integer pageNo,
                                         @RequestParam(value = "pageSize", required = false) Integer pageSize);

    @GetMapping("/questions/biz/{bizId}")
    R<List<Object>> queryBizQuestions(@PathVariable("bizId") Long bizId);

    @PostMapping("/admin/questions/{id}/biz")
    R<Void> bindToBiz(@PathVariable("id") Long id,
                      @RequestParam("bizId") Long bizId,
                      @RequestParam(value = "score", required = false) Integer score);
}
