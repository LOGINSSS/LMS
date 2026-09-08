package com.lms.exam.exam.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lms.common.enums.UserType;
import com.lms.common.exceptions.CommonException;
import com.lms.common.exceptions.ForbiddenException;
import com.lms.common.utils.AssertUtils;
import com.lms.common.utils.UserContext;
import com.lms.exam.exam.domain.dto.PaperCreateFormDTO;
import com.lms.exam.exam.domain.dto.PaperSubmitDTO;
import com.lms.exam.exam.domain.po.ExamPaper;
import com.lms.exam.exam.domain.po.ExamPaperItem;
import com.lms.exam.exam.domain.po.Question;
import com.lms.exam.exam.domain.vo.ExamPaperItemVO;
import com.lms.exam.exam.domain.vo.ExamPaperVO;
import com.lms.exam.exam.mapper.ExamPaperItemMapper;
import com.lms.exam.exam.mapper.ExamPaperMapper;
import com.lms.exam.exam.mapper.QuestionMapper;
import com.lms.exam.exam.service.IExamPaperService;
import com.lms.exam.exam.util.PaperGrader;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 试卷快照服务实现（v1 收尾：出卷流程骨干）
 *
 * - 组卷（create）：老师从题库选题 → 快照题干/答案/解析进卷（草稿态），题库内容不下发学生；
 * - 发布（publish）：owner 发布并汇总总分；
 * - 答题（studentView/submit）：仅已发布卷；学生端拿题干渲染（答案/解析剥离），
 *   提交后由 PaperGrader 按快照答案确定性判分（作业允许多次重做刷当题分，服务端判分保证诚实）。
 * - 权限：组卷/发布/管理仅教师（userType=2）；学生 view/submit 需登录。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ExamPaperServiceImpl implements IExamPaperService {

    private final ExamPaperMapper paperMapper;
    private final ExamPaperItemMapper itemMapper;
    private final QuestionMapper questionMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long create(PaperCreateFormDTO dto) {
        assertTeacher();
        AssertUtils.isTrue(dto.getItems() != null && !dto.getItems().isEmpty(), "卷面至少一题");
        // 去重选题
        Set<Long> ids = dto.getItems().stream().map(PaperCreateFormDTO.ItemSel::getId).collect(Collectors.toSet());
        AssertUtils.isTrue(ids.size() == dto.getItems().size(), "选题重复，同一题只可组一次");
        // 题库取题（仅启用状态）
        List<Question> questions = questionMapper.selectList(new LambdaQueryWrapper<Question>()
                .in(Question::getId, ids)
                .eq(Question::getStatus, 1));
        Map<Long, Question> qMap = questions.stream().collect(Collectors.toMap(Question::getId, q -> q));
        Set<Long> missing = new HashSet<>(ids);
        missing.removeAll(qMap.keySet());
        AssertUtils.isTrue(missing.isEmpty(), "以下题目不存在或已停用: " + missing);

        ExamPaper paper = new ExamPaper();
        paper.setTitle(dto.getTitle());
        paper.setDescription(dto.getDescription());
        paper.setCourseId(dto.getCourseId());
        paper.setTeacherId(UserContext.getUser());
        paper.setTotalScore(0);
        paper.setStatus(ExamPaper.STATUS_DRAFT);
        paperMapper.insert(paper);

        int seq = 1;
        int total = 0;
        for (PaperCreateFormDTO.ItemSel sel : dto.getItems()) {
            Question q = qMap.get(sel.getId());
            ExamPaperItem item = new ExamPaperItem();
            item.setPaperId(paper.getId());
            item.setSeq(seq++);
            item.setQuestionId(q.getId());
            item.setStem(q.getName());
            item.setType(q.getType());
            item.setCategory(q.getCategory());
            item.setDifficulty(q.getDifficulty());
            int score = sel.getScore() == null || sel.getScore() <= 0 ? 1 : sel.getScore();
            item.setScore(score);
            item.setAnswer(q.getAnswer());
            item.setAnalysis(q.getAnalysis());
            itemMapper.insert(item);
            total += score;
        }
        paper.setTotalScore(total);
        paperMapper.updateById(paper);
        return paper.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void publish(Long id) {
        assertTeacher();
        ExamPaper paper = getEntity(id);
        assertOwner(paper);
        List<ExamPaperItem> items = loadItems(id);
        int total = items.stream().mapToInt(it -> it.getScore() == null ? 0 : it.getScore()).sum();
        paper.setTotalScore(total);
        paper.setStatus(ExamPaper.STATUS_PUBLISHED);
        paperMapper.updateById(paper);
    }

    @Override
    public List<ExamPaperVO> listMine() {
        assertTeacher();
        List<ExamPaper> papers = paperMapper.selectList(new LambdaQueryWrapper<ExamPaper>()
                .eq(ExamPaper::getTeacherId, UserContext.getUser())
                .orderByDesc(ExamPaper::getId));
        List<ExamPaperVO> out = new ArrayList<>();
        for (ExamPaper p : papers) {
            out.add(toVO(p, loadItems(p.getId()), true));
        }
        return out;
    }

    @Override
    public ExamPaperVO adminView(Long id) {
        assertTeacher();
        ExamPaper paper = getEntity(id);
        assertOwner(paper);
        return toVO(paper, loadItems(id), true);
    }

    @Override
    public ExamPaperVO studentView(Long id) {
        ExamPaper paper = getEntity(id);
        AssertUtils.isTrue(paper.getStatus() != null && paper.getStatus() == ExamPaper.STATUS_PUBLISHED,
                "该卷面未发布或不可用");
        return toVO(paper, loadItems(id), false);
    }

    @Override
    public Map<String, Object> submit(Long id, PaperSubmitDTO dto) {
        ExamPaper paper = getEntity(id);
        AssertUtils.isTrue(paper.getStatus() != null && paper.getStatus() == ExamPaper.STATUS_PUBLISHED,
                "该卷面未发布或已停用");
        AssertUtils.isTrue(dto != null && dto.getAnswers() != null && !dto.getAnswers().isEmpty(), "作答不能为空");

        List<ExamPaperItem> items = loadItems(id);
        Map<Long, ExamPaperItem> byQid = items.stream().collect(Collectors.toMap(ExamPaperItem::getQuestionId, it -> it));

        int totalScore = paper.getTotalScore() == null ? 0 : paper.getTotalScore();
        int score = 0;
        int correct = 0;
        List<Map<String, Object>> details = new ArrayList<>();
        for (PaperSubmitDTO.Answer ans : dto.getAnswers()) {
            ExamPaperItem item = byQid.get(ans.getQuestionId());
            if (item == null) {
                continue; // 非本卷题目忽略
            }
            boolean ok = PaperGrader.check(item.getType(), item.getAnswer(),
                    ans.getUserAnswer() == null ? "" : ans.getUserAnswer());
            int s = ok ? (item.getScore() == null ? 1 : item.getScore()) : 0;
            if (ok) {
                correct++;
            }
            score += s;
            Map<String, Object> d = new LinkedHashMap<>();
            d.put("questionId", item.getQuestionId());
            d.put("seq", item.getSeq());
            d.put("correct", ok ? 1 : 0);
            d.put("score", s);
            details.add(d);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("paperId", paper.getId());
        result.put("title", paper.getTitle());
        result.put("totalScore", totalScore);
        result.put("questionCount", items.size());
        result.put("answeredCount", details.size());
        result.put("correctCount", correct);
        result.put("score", score);
        result.put("details", details);
        log.info("试卷判分完成 paperId={} score={}/{}", id, score, totalScore);
        return result;
    }

    // ---------- 内部 ----------

    private ExamPaper getEntity(Long id) {
        ExamPaper paper = paperMapper.selectById(id);
        AssertUtils.notNull(paper, "卷面不存在");
        return paper;
    }

    private List<ExamPaperItem> loadItems(Long paperId) {
        return itemMapper.selectList(new LambdaQueryWrapper<ExamPaperItem>()
                .eq(ExamPaperItem::getPaperId, paperId)
                .orderByAsc(ExamPaperItem::getSeq));
    }

    private ExamPaperVO toVO(ExamPaper paper, List<ExamPaperItem> items, boolean includeAnswers) {
        ExamPaperVO vo = new ExamPaperVO();
        vo.setId(paper.getId());
        vo.setTitle(paper.getTitle());
        vo.setDescription(paper.getDescription());
        vo.setCourseId(paper.getCourseId());
        vo.setTeacherId(paper.getTeacherId());
        vo.setStatus(paper.getStatus());
        vo.setTotalScore(paper.getTotalScore());
        List<ExamPaperItemVO> itemVOs = new ArrayList<>();
        for (ExamPaperItem it : items) {
            ExamPaperItemVO iv = new ExamPaperItemVO();
            iv.setSeq(it.getSeq());
            iv.setQuestionId(it.getQuestionId());
            iv.setStem(it.getStem());
            iv.setType(it.getType());
            iv.setCategory(it.getCategory());
            iv.setDifficulty(it.getDifficulty());
            iv.setScore(it.getScore());
            if (includeAnswers) {
                iv.setAnswer(it.getAnswer());
                iv.setAnalysis(it.getAnalysis());
            }
            itemVOs.add(iv);
        }
        vo.setItems(itemVOs);
        return vo;
    }

    private void assertOwner(ExamPaper paper) {
        if (!Objects.equals(paper.getTeacherId(), UserContext.getUser())) {
            throw new ForbiddenException("仅组卷者可操作该卷面");
        }
    }

    private void assertTeacher() {
        Integer userType = UserContext.getUserType();
        Long userId = UserContext.getUser();
        if (userId == null || userType == null || userType != UserType.TEACHER.getValue()) {
            throw new ForbiddenException("仅教师可管理卷面");
        }
    }
}
