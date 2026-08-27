package com.lms.learning.learning.controller;

import com.lms.common.domain.R;
import com.lms.learning.learning.domain.vo.MyLearnStatsVO;
import com.lms.learning.learning.service.ILearningService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 我的学习统计接口
 *
 * 职责：学生学习概览页的数据聚合（笔记/提问/回答/签到/积分），
 * 只接收参数并调用服务，不承载业务逻辑。
 */
@Tag(name = "我的学习统计接口")
@RestController
@RequestMapping("/learn")
@RequiredArgsConstructor
public class LearnStatsController {

    private final ILearningService learningService;

    /** 我的学习统计（当前登录用户累计值） */
    @GetMapping("/stats/my")
    @Operation(summary = "我的学习统计")
    public R<MyLearnStatsVO> myStats() {
        return R.ok(learningService.myLearnStats());
    }
}
