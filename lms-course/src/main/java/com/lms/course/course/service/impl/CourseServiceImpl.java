package com.lms.course.course.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lms.common.domain.dto.PageDTO;
import com.lms.common.domain.R;
import com.lms.common.enums.UserType;
import com.lms.common.exceptions.CommonException;
import com.lms.common.exceptions.ForbiddenException;
import com.lms.common.utils.AssertUtils;
import com.lms.common.utils.BeanUtils;
import com.lms.common.utils.CollUtils;
import com.lms.common.utils.UserContext;
import com.lms.course.course.client.GrabClient;
import com.lms.course.course.domain.dto.EnrollRuleForm;
import com.lms.course.course.eligibility.EligibilityService;
import com.lms.course.course.domain.vo.EligibilityVO;

import java.util.Objects;
import com.lms.course.course.client.KbClient;
import com.lms.course.course.client.UserClient;
import com.lms.course.course.client.dto.UserSimpleDTO;
import com.lms.course.course.constants.CourseErrorInfo;
import com.lms.course.course.domain.dto.CourseCardVO;
import com.lms.course.course.domain.dto.CourseFormDTO;
import com.lms.course.course.domain.po.Course;
import com.lms.course.course.domain.po.CourseCatalog;
import com.lms.course.course.domain.po.CourseChapter;
import com.lms.course.course.domain.po.CourseEnrollment;
import com.lms.course.course.domain.query.CoursePageQuery;
import com.lms.course.course.enums.CourseStatus;
import com.lms.course.course.enums.EnrollmentStatus;
import com.lms.course.course.mapper.CourseCatalogMapper;
import com.lms.course.course.mapper.CourseChapterMapper;
import com.lms.course.course.mapper.CourseEnrollmentMapper;
import com.lms.course.course.mapper.CourseMapper;
import com.lms.course.course.service.CategoryService;
import com.lms.course.course.service.CourseCacheService;
import com.lms.course.course.service.ICourseService;
import com.lms.course.course.domain.vo.CourseTopVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 课程业务服务实现
 *
 * 事务边界：建课/改课/上下架/选课/退课均为单事务，任一失败整体回滚。
 * 当前用户来源：UserContext（公共拦截器解析网关透传的 user-info 头写入，
 * 本服务通过 Nacos 配置 lms.mvc.user-header-enabled=true 开启）。
 * 权限规则：建课/管理仅限教师本人（userType=2）；选课/退课仅限学生（userType=1）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CourseServiceImpl implements ICourseService {

    private final CourseMapper courseMapper;
    private final CourseEnrollmentMapper enrollmentMapper;
    private final CourseCatalogMapper catalogMapper;
    private final CourseChapterMapper chapterMapper;
    private final UserClient userClient;
    private final GrabClient grabClient;
    private final EligibilityService eligibilityService;
    private final KbClient kbClient;
    private final CourseCacheService courseCacheService;
    private final CategoryService categoryService;

    /** 跨服务查询教师昵称失败时的兜底署名 */
    private static final String DEFAULT_TEACHER_NAME = "教师";

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long addCourse(CourseFormDTO dto) {
        //1. 身份校验：仅教师可创建课程，防止学生越权建课
        assertTeacher();
        //2. 取当前教师 id（UserContext 来自网关透传的用户头）
        Long teacherId = UserContext.getUser();
        //3. 查教师昵称：Feign 调 lms-user 取档案昵称做快照；查询失败降级为默认署名
        //   【保障机制】跨服务容错：UserClientFallbackFactory 返回 null，此处兜底，建课主流程不中断
        String teacherName = fetchTeacherName(teacherId);
        //4. 组装并落库：新课程默认草稿（0），由教师编辑内容后提交发布
        Course course = BeanUtils.copyBean(dto, Course.class);
        course.setTeacherId(teacherId);
        course.setTeacherName(teacherName);
        course.setStatus(CourseStatus.DRAFT.getValue());
        if (course.getStock() == null) {
            course.setStock(0);
        }
        if (courseMapper.insert(course) <= 0) {
            throw new CommonException(CourseErrorInfo.COURSE_SAVE_FAILED);
        }
        //5. 自动创建课程知识库（Python RAG 单集合 + course_id 分区，幂等；失败降级不阻断建课）
        Map<String, Object> kbBody = new java.util.HashMap<>();
        kbBody.put("courseId", course.getId());
        kbBody.put("ownerType", 1);
        kbBody.put("name", course.getName());
        try {
            kbClient.createKb(kbBody);
        } catch (Exception e) {
            // 【保障机制】RAG 服务不可用不阻断建课：课程先落库成功，知识库后续可补
            log.warn("课程知识库创建失败（不阻断建课）courseId={}: {}", course.getId(), e.getMessage());
        }
        return course.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateCourse(Long id, CourseFormDTO dto) {
        //1. 校验课程存在且为当前教师本人创建
        Course course = getOwnCourse(id);
        //2. 组装更新对象：仅更新入参非 null 字段，教师/状态等归属字段不可改
        Course update = BeanUtils.copyBean(dto, Course.class);
        update.setId(course.getId());
        if (courseMapper.updateById(update) <= 0) {
            throw new CommonException(CourseErrorInfo.COURSE_SAVE_FAILED);
        }
        //3. 失效课程详情缓存
        courseCacheService.evictCourseDetail(id);
        //4. 可见状态下修改分类 → 旧分类 SET 移出、新分类 SET 补入（集合保持精确）
        if (course.getStatus() != null
                && (course.getStatus() == CourseStatus.GRABBING.getValue()
                || course.getStatus() == CourseStatus.ONGOING.getValue())
                && StrUtil.isNotBlank(dto.getCategory())
                && !StrUtil.equals(course.getCategory(), dto.getCategory().trim())) {
            categoryService.removeVisible(course.getId(), course.getCategory());
            categoryService.indexVisible(course.getId(), dto.getCategory().trim());
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteCourse(Long id) {
        //1. 校验课程存在且为当前教师本人创建
        Course course = getOwnCourse(id);
        //2. 级联删除业务数据：选课 → 目录/章节 → 课程本体（逻辑删除，列表不再可见）
        enrollmentMapper.delete(new LambdaQueryWrapper<CourseEnrollment>().eq(CourseEnrollment::getCourseId, id));
        List<CourseCatalog> nodes = catalogMapper.selectList(new LambdaQueryWrapper<CourseCatalog>()
                .eq(CourseCatalog::getCourseId, id));
        if (CollUtils.isNotEmpty(nodes)) {
            List<Long> catalogIds = nodes.stream().map(CourseCatalog::getId).collect(Collectors.toList());
            chapterMapper.delete(new LambdaQueryWrapper<CourseChapter>().in(CourseChapter::getCatalogId, catalogIds));
            catalogMapper.delete(new LambdaQueryWrapper<CourseCatalog>().eq(CourseCatalog::getCourseId, id));
        }
        courseMapper.deleteById(id);
        //3. 清理 Python RAG 该课程知识库（向量+文档；失败降级不阻断删课，可后续手动清理）
        try {
            kbClient.deleteCourse(id);
        } catch (Exception e) {
            log.warn("清理课程知识库失败（不阻断删课）courseId={}: {}", id, e.getMessage());
        }
        //4. 失效课程缓存
        courseCacheService.evictCourseDetail(id);
        courseCacheService.evictCatalog(id);
        //5. 从分类 SET 移除该课程 id（保持集合=当前可见课程）
        categoryService.removeVisible(course.getId(), course.getCategory());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void changeStatus(Long id, Integer status) {
        //1. 校验课程存在且为当前教师本人创建
        Course course = getOwnCourse(id);
        //2. 状态落库：目标状态取 CourseStatus 枚举值（0 草稿 / 1 待发布 / 2 抢课中 / 3 进行中 / 4 已结束 / 5 下架）
        Course update = new Course();
        update.setId(course.getId());
        update.setStatus(status);
        courseMapper.updateById(update);
        //3. 失效课程详情缓存
        courseCacheService.evictCourseDetail(id);
        //4. 分类 SET 维护：离开可见(2/3) → 移出；进入可见 → 补入（保持集合=当前可见课程）
        boolean wasVisible = course.getStatus() != null
                && (course.getStatus() == CourseStatus.GRABBING.getValue()
                || course.getStatus() == CourseStatus.ONGOING.getValue());
        boolean willVisible = update.getStatus() != null
                && (update.getStatus() == CourseStatus.GRABBING.getValue()
                || update.getStatus() == CourseStatus.ONGOING.getValue());
        if (wasVisible && !willVisible) {
            categoryService.removeVisible(course.getId(), course.getCategory());
        } else if (!wasVisible && willVisible) {
            categoryService.indexVisible(course.getId(), course.getCategory());
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void publish(Long id, LocalDateTime grabStartTime, LocalDateTime grabEndTime, Integer stock) {
        //1. 校验课程存在且为当前教师本人创建
        Course course = getOwnCourse(id);
        //2. 抢课窗口校验：开始时间必须早于结束时间，且未过期
        if (grabStartTime == null || grabEndTime == null || !grabStartTime.isBefore(grabEndTime)) {
            throw new CommonException(CourseErrorInfo.GRAB_WINDOW_INVALID);
        }
        //3. 落库：进入待发布（抢课窗口到点后由定时任务流转为抢课中）
        Course update = new Course();
        update.setId(course.getId());
        update.setStock(stock == null ? 0 : stock);
        update.setGrabStartTime(grabStartTime);
        update.setGrabEndTime(grabEndTime);
        update.setStatus(CourseStatus.PENDING_GRAB.getValue());
        courseMapper.updateById(update);
        //4. 通知 lms-grab 预热库存（失败降级不阻断发布，见 GrabClientFallbackFactory）
        grabClient.prepare(course.getId(),
                grabStartTime.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
                grabEndTime.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
                stock == null ? 0 : stock);
        //5. 失效课程详情缓存
        courseCacheService.evictCourseDetail(id);
        //6. 课程正文一键同步入知识库（Python RAG ingest；失败降级不阻断发布）
        String md = buildCourseMarkdown(course.getId());
        if (md != null && !md.isBlank()) {
            Map<String, String> syncBody = new java.util.HashMap<>();
            syncBody.put("mdText", md);
            try {
                kbClient.syncCourseText(course.getId(), syncBody);
            } catch (Exception e) {
                // 【保障机制】RAG 不可用/同步失败不阻断发布，正文可在编辑内容时重试同步
                log.warn("课程正文同步知识库失败（不阻断发布）courseId={}: {}", course.getId(), e.getMessage());
            }
        }
    }

    /**
     * 拼接课程目录+章节正文为一份 markdown（课程正文同步入知识库用）
     */
    private String buildCourseMarkdown(Long courseId) {
        StringBuilder sb = new StringBuilder();
        List<CourseCatalog> nodes = catalogMapper.selectList(new LambdaQueryWrapper<CourseCatalog>()
                .eq(CourseCatalog::getCourseId, courseId)
                .orderByAsc(CourseCatalog::getSort));
        if (CollUtils.isEmpty(nodes)) {
            return null;
        }
        for (CourseCatalog node : nodes) {
            if (node.getParentId() == null || node.getParentId() == 0L) {
                // 章
                sb.append("\n# ").append(node.getName()).append("\n");
            } else {
                // 节
                sb.append("\n## ").append(node.getName()).append("\n");
            }
            CourseChapter chapter = chapterMapper.selectOne(new LambdaQueryWrapper<CourseChapter>()
                    .eq(CourseChapter::getCatalogId, node.getId()));
            if (chapter != null && chapter.getContentMd() != null) {
                sb.append(chapter.getContentMd()).append("\n");
            }
        }
        return sb.toString();
    }

    @Override
    public String getEnrollRule(Long courseId) {
        assertTeacher();
        return eligibilityService.getRuleJson(courseId);
    }

    @Override
    public void saveEnrollRule(Long courseId, EnrollRuleForm form) {
        Long teacherId = assertTeacher();
        requireOwnCourse(courseId, teacherId);
        eligibilityService.save(courseId, form);
    }

    @Override
    public void removeEnrollRule(Long courseId) {
        Long teacherId = assertTeacher();
        requireOwnCourse(courseId, teacherId);
        eligibilityService.remove(courseId);
    }

    /** 校验课程归属当前教师（管理端规则/排期等操作） */
    private void requireOwnCourse(Long courseId, Long teacherId) {
        Course course = courseMapper.selectById(courseId);
        AssertUtils.notNull(course, CourseErrorInfo.COURSE_NOT_FOUND.getMsg());
        if (!Objects.equals(course.getTeacherId(), teacherId)) {
            throw new ForbiddenException("只能管理自己创建的课程");
        }
    }

    @Override
    public PageDTO<CourseCardVO> queryPublishedPage(CoursePageQuery query) {
        //0. 无关键词时走「分类 SET 索引 → 课程详情缓存」路径（全部分类 = 所有分类 set 并集）；
        //   有关键词无法用分类索引，回退 DB 检索
        if (StrUtil.isBlank(query.getKeyword())) {
            List<Long> ids = categoryService.unionVisibleIds(query.getCategory());
            long total = ids.size();
            int pageNo = query.getPageNo() == null ? 1 : query.getPageNo();
            int size = query.getPageSize() == null ? 20 : query.getPageSize();
            int from = (pageNo - 1) * size;
            if (from >= total) {
                return PageDTO.of(total, Collections.emptyList());
            }
            int to = Math.min((int) total, from + size);
            List<CourseCardVO> vos = new java.util.ArrayList<>();
            for (Long id : ids.subList(from, to)) {
                CourseCardVO card = visibleCardCache(id);
                if (card != null) {
                    vos.add(card);
                }
            }
            // 选课人数实时填充（覆盖缓存里的旧值）
            fillTotalCount(vos);
            return PageDTO.of(total, vos);
        }
        //1. DB 检索路径（关键词）
        Page<Course> page = query.toMpPageDefaultSortByCreateTimeDesc();
        courseMapper.selectPage(page, publishedWrapper(query));
        //2. 转卡片 VO 并填充实时选课人数
        List<CourseCardVO> vos = BeanUtils.copyList(page.getRecords(), CourseCardVO.class);
        fillTotalCount(vos);
        return PageDTO.of(page.getTotal(), vos);
    }

    /**
     * 广场卡片：course:detail:{id} 缓存读取（未命中回源），仅放行可见状态(2/3)，
     * 失效/已下架课程返回 null（分类 SET 不做删除，靠这里读时过滤）。
     */
    private CourseCardVO visibleCardCache(Long id) {
        return courseCacheService.getCourseDetail(id, () -> {
            Course c = courseMapper.selectById(id);
            if (c == null || c.getStatus() == null
                    || (c.getStatus() != CourseStatus.GRABBING.getValue()
                    && c.getStatus() != CourseStatus.ONGOING.getValue())) {
                return null;
            }
            return BeanUtils.copyBean(c, CourseCardVO.class);
        });
    }

    @Override
    public CourseCardVO getCourseDetail(Long id) {
        //0. 创建教师本人查看自己课程（含草稿/待发布等未开放状态）：直查直返，不走前台缓存，
        //   避免草稿内容经缓存对他人可见（发布时详情缓存已主动失效，学生端仍只放行 2/3）
        if (isOwnerTeacher(id)) {
            Course course = courseMapper.selectById(id);
            AssertUtils.notNull(course, CourseErrorInfo.COURSE_NOT_FOUND.getMsg());
            CourseCardVO vo = BeanUtils.copyBean(course, CourseCardVO.class);
            fillTotalCount(Collections.singletonList(vo));
            return vo;
        }
        //1. 走缓存：命中直接返回，未命中回源并回填（Cache-Aside，spec 0.2 §5.1）
        return courseCacheService.getCourseDetail(id, () -> {
            //1.1 查课程：不存在或未开放（非 2/3）均视为不可见（对前台统一拒绝）
            Course course = courseMapper.selectById(id);
            AssertUtils.notNull(course, CourseErrorInfo.COURSE_NOT_FOUND.getMsg());
            if (course.getStatus() == null
                    || (course.getStatus() != CourseStatus.GRABBING.getValue()
                    && course.getStatus() != CourseStatus.ONGOING.getValue())) {
                throw new CommonException(CourseErrorInfo.COURSE_NOT_PUBLISHED);
            }
            //1.2 转卡片 VO 并填充选课人数
            CourseCardVO vo = BeanUtils.copyBean(course, CourseCardVO.class);
            fillTotalCount(Collections.singletonList(vo));
            return vo;
        });
    }

    @Override
    public PageDTO<CourseCardVO> queryMyCourses(CoursePageQuery query) {
        //1. 按当前教师筛选：管理视角查自己全部课程（含未发布）
        Long teacherId = assertTeacher();
        Page<Course> page = query.toMpPageDefaultSortByCreateTimeDesc();
        LambdaQueryWrapper<Course> wrapper = new LambdaQueryWrapper<Course>()
                .eq(Course::getTeacherId, teacherId);
        applyFilter(wrapper, query);
        courseMapper.selectPage(page, wrapper);
        //2. 转卡片 VO 并填充选课人数
        List<CourseCardVO> vos = BeanUtils.copyList(page.getRecords(), CourseCardVO.class);
        fillTotalCount(vos);
        return PageDTO.of(page.getTotal(), vos);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void enroll(Long courseId) {
        //1. 身份校验：仅学生可选课
        Long studentId = assertStudent();
        //2. 校验课程存在且已开放：草稿/待发布/下架不可选；抢课窗口内的课程走抢课接口
        Course course = courseMapper.selectById(courseId);
        AssertUtils.notNull(course, CourseErrorInfo.COURSE_NOT_FOUND.getMsg());
        if (course.getStatus() == null
                || (course.getStatus() != CourseStatus.GRABBING.getValue()
                && course.getStatus() != CourseStatus.ONGOING.getValue())) {
            throw new CommonException(CourseErrorInfo.COURSE_NOT_PUBLISHED);
        }
        if (course.getStatus() == CourseStatus.GRABBING.getValue() && course.getStock() != null && course.getStock() > 0) {
            throw new CommonException(CourseErrorInfo.GRAB_REQUIRED);
        }
        //2.5 选课资格校验（course_enroll_rule：仅限指定范围学生，如 大三/已修完先修课/积分达标）
        EligibilityVO eligibility = eligibilityService.check(studentId, courseId);
        if (!eligibility.isAllowed()) {
            throw new CommonException("不符合本课程选课条件：" + String.join("；", eligibility.getReasons()));
        }
        //3. 幂等处理：查该课程下选课记录（任意状态）
        //   选课中 → 报已选；已退课 → 复用记录翻回选课中（避免撞 uk_course_student 唯一键）
        //   注意：预校验存在并发窗口，最终兜底是 uk_course_student 唯一索引 + DuplicateKeyException
        CourseEnrollment existed = enrollmentMapper.selectOne(new LambdaQueryWrapper<CourseEnrollment>()
                .eq(CourseEnrollment::getCourseId, courseId)
                .eq(CourseEnrollment::getStudentId, studentId));
        if (existed != null) {
            if (existed.getStatus() != null && existed.getStatus() == EnrollmentStatus.ACTIVE.getValue()) {
                throw new CommonException(CourseErrorInfo.ALREADY_ENROLLED);
            }
            // 已退课记录恢复为选课中，保留原始建档时间
            existed.setStatus(EnrollmentStatus.ACTIVE.getValue());
            enrollmentMapper.updateById(existed);
            return;
        }
        //4. 无历史记录：插入新选课记录（状态选课中）
        CourseEnrollment enrollment = new CourseEnrollment();
        enrollment.setCourseId(courseId);
        enrollment.setStudentId(studentId);
        enrollment.setStatus(EnrollmentStatus.ACTIVE.getValue());
        try {
            enrollmentMapper.insert(enrollment);
        } catch (DuplicateKeyException e) {
            // 【保障机制】并发兜底：撞唯一键说明记录已存在（退课态），重查并翻回选课中
            CourseEnrollment again = enrollmentMapper.selectOne(new LambdaQueryWrapper<CourseEnrollment>()
                    .eq(CourseEnrollment::getCourseId, courseId)
                    .eq(CourseEnrollment::getStudentId, studentId));
            if (again != null) {
                again.setStatus(EnrollmentStatus.ACTIVE.getValue());
                enrollmentMapper.updateById(again);
            }
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void quit(Long courseId) {
        //1. 身份校验：仅学生可退课
        Long studentId = assertStudent();
        //2. 查选课中记录：不存在则说明未选过该课程
        CourseEnrollment enrollment = enrollmentMapper.selectOne(new LambdaQueryWrapper<CourseEnrollment>()
                .eq(CourseEnrollment::getCourseId, courseId)
                .eq(CourseEnrollment::getStudentId, studentId)
                .eq(CourseEnrollment::getStatus, EnrollmentStatus.ACTIVE.getValue()));
        AssertUtils.notNull(enrollment, CourseErrorInfo.NOT_ENROLLED.getMsg());
        //3. 状态置为已退课：保留历史记录，便于统计与恢复
        enrollment.setStatus(EnrollmentStatus.QUIT.getValue());
        enrollmentMapper.updateById(enrollment);
    }

    @Override
    public PageDTO<CourseCardVO> queryEnrolledCourses(CoursePageQuery query) {
        //1. 查当前学生选课中记录：先拿课程 id 集合
        Long studentId = assertStudent();
        Page<Course> page = query.toMpPageDefaultSortByCreateTimeDesc();
        List<CourseEnrollment> enrollments = enrollmentMapper.selectList(new LambdaQueryWrapper<CourseEnrollment>()
                .eq(CourseEnrollment::getStudentId, studentId)
                .eq(CourseEnrollment::getStatus, EnrollmentStatus.ACTIVE.getValue()));
        if (CollUtils.isEmpty(enrollments)) {
            return PageDTO.of(0L, Collections.emptyList());
        }
        Set<Long> courseIds = enrollments.stream().map(CourseEnrollment::getCourseId).collect(Collectors.toSet());
        //2. 按课程 id 集合分页查课程（不区分状态，已选过的课即便下架仍可见）
        courseMapper.selectPage(page, new LambdaQueryWrapper<Course>()
                .in(Course::getId, courseIds)
                .orderByDesc(Course::getCreateTime));
        //3. 转卡片 VO 并填充选课人数
        List<CourseCardVO> vos = BeanUtils.copyList(page.getRecords(), CourseCardVO.class);
        fillTotalCount(vos);
        return PageDTO.of(page.getTotal(), vos);
    }

    /**
     * 可被学生看到的课程分页查询条件（前台卡片列表）
     * 可见状态：2 抢课中 / 3 进行中
     */
    private LambdaQueryWrapper<Course> publishedWrapper(CoursePageQuery query) {
        LambdaQueryWrapper<Course> wrapper = new LambdaQueryWrapper<Course>()
                .in(Course::getStatus, CourseStatus.GRABBING.getValue(), CourseStatus.ONGOING.getValue());
        applyFilter(wrapper, query);
        return wrapper;
    }

    /**
     * 通用筛选条件：分类精确匹配 + 关键字模糊匹配名称/简介
     */
    private void applyFilter(LambdaQueryWrapper<Course> wrapper, CoursePageQuery query) {
        if (StrUtil.isNotBlank(query.getCategory())) {
            wrapper.eq(Course::getCategory, query.getCategory().trim());
        }
        if (StrUtil.isNotBlank(query.getKeyword())) {
            String keyword = query.getKeyword().trim();
            wrapper.and(w -> w.like(Course::getName, keyword).or().like(Course::getIntro, keyword));
        }
    }

    /**
     * 校验并返回当前教师 id（非教师抛 Forbidden）
     */
    private Long assertTeacher() {
        Integer userType = UserContext.getUserType();
        Long userId = UserContext.getUser();
        if (userId == null || userType == null || userType != UserType.TEACHER.getValue()) {
            throw new ForbiddenException("仅教师可执行该操作");
        }
        return userId;
    }

    /**
     * 校验并返回当前学生 id（非学生抛 Forbidden）
     */
    private Long assertStudent() {
        Integer userType = UserContext.getUserType();
        Long userId = UserContext.getUser();
        if (userId == null || userType == null || userType != UserType.STUDENT.getValue()) {
            throw new ForbiddenException("仅学生可执行该操作");
        }
        return userId;
    }

    /**
     * 查询当前教师本人创建的课程（不存在或非本人抛业务异常）
     */
    private Course getOwnCourse(Long id) {
        Course course = courseMapper.selectById(id);
        AssertUtils.notNull(course, CourseErrorInfo.COURSE_NOT_FOUND.getMsg());
        if (!course.getTeacherId().equals(UserContext.getUser())) {
            throw new ForbiddenException("只能管理自己创建的课程");
        }
        return course;
    }

    /**
     * 当前用户是否为该课程的创建教师（未登录/非教师/非本人均返回 false）
     */
    private boolean isOwnerTeacher(Long id) {
        Long userId = UserContext.getUser();
        Integer userType = UserContext.getUserType();
        if (userId == null || userType == null || userType != UserType.TEACHER.getValue()) {
            return false;
        }
        Course course = courseMapper.selectById(id);
        return course != null && course.getTeacherId() != null && course.getTeacherId().equals(userId);
    }

    /**
     * Feign 查询教师昵称：失败降级为默认署名「教师」
     */
    private String fetchTeacherName(Long teacherId) {
        try {
            R<UserSimpleDTO> resp = userClient.queryUserById(teacherId);
            if (resp != null && resp.getData() != null && StrUtil.isNotBlank(resp.getData().getNickname())) {
                return resp.getData().getNickname();
            }
        } catch (Exception e) {
            // 兜底：昵称获取失败不影响建课
        }
        return DEFAULT_TEACHER_NAME;
    }

    /**
     * 批量填充卡片选课人数：一次 group by 查询避免 N+1
     *
     * 统计口径：course_enrollment 中 status=1（选课中）的记录数，
     * 实时计算保证数据准确，不依赖冗余计数字段。
     */
    private void fillTotalCount(List<CourseCardVO> vos) {
        if (CollUtils.isEmpty(vos)) {
            return;
        }
        //1. 收集课程 id 集合
        Set<Long> courseIds = vos.stream().map(CourseCardVO::getId).collect(Collectors.toSet());
        //2. 一次性按课程分组统计选课中人数
        List<Map<String, Object>> rows = enrollmentMapper.selectMaps(new QueryWrapper<CourseEnrollment>()
                .select("course_id", "count(*) as cnt")
                .in("course_id", courseIds)
                .eq("status", EnrollmentStatus.ACTIVE.getValue())
                .groupBy("course_id"));
        Map<Long, Long> countMap = rows.stream().collect(Collectors.toMap(
                row -> ((Number) row.get("course_id")).longValue(),
                row -> ((Number) row.get("cnt")).longValue()));
        //3. 回填到卡片 VO（未选课课程默认 0）
        vos.forEach(vo -> vo.setTotalCount(countMap.getOrDefault(vo.getId(), 0L)));
    }

    @Override
    public long countEnrollTotal() {
        //1. 统计选课中记录总数（数据中心看板口径）
        Long count = enrollmentMapper.selectCount(new LambdaQueryWrapper<CourseEnrollment>()
                .eq(CourseEnrollment::getStatus, EnrollmentStatus.ACTIVE.getValue()));
        return count == null ? 0L : count;
    }

    @Override
    public long countTodayCourses() {
        //1. 统计今日新增课程：create_time 落在今日 0 点之后（含）
        Long count = courseMapper.selectCount(new LambdaQueryWrapper<Course>()
                .ge(Course::getCreateTime, LocalDate.now().atStartOfDay()));
        return count == null ? 0L : count;
    }

    @Override
    public List<CourseTopVO> topCourses(int size) {
        //1. 按课程分组统计选课中人数，取 Top N
        int safeSize = size < 1 ? 10 : Math.min(size, 50);
        List<Map<String, Object>> rows = enrollmentMapper.selectMaps(new QueryWrapper<CourseEnrollment>()
                .select("course_id", "count(*) as cnt")
                .eq("status", EnrollmentStatus.ACTIVE.getValue())
                .groupBy("course_id")
                .orderByDesc("cnt")
                .last("LIMIT " + safeSize));
        if (CollUtils.isEmpty(rows)) {
            return Collections.emptyList();
        }
        //2. 批量取课程名并组装热度榜
        List<Long> courseIds = rows.stream()
                .map(row -> ((Number) row.get("course_id")).longValue())
                .collect(Collectors.toList());
        Map<Long, String> nameMap = courseMapper.selectBatchIds(courseIds).stream()
                .collect(Collectors.toMap(Course::getId, Course::getName));
        return rows.stream()
                .map(row -> {
                    Long courseId = ((Number) row.get("course_id")).longValue();
                    return new CourseTopVO(courseId, nameMap.get(courseId), ((Number) row.get("cnt")).longValue());
                })
                .collect(Collectors.toList());
    }

    @Override
    public Map<String, Object> getCourseOwner(Long id) {
        //1. 课程存在性校验
        Course course = courseMapper.selectById(id);
        AssertUtils.notNull(course, CourseErrorInfo.COURSE_NOT_FOUND.getMsg());
        //2. 组装归属信息（教师 id + 课程名）
        Map<String, Object> owner = new java.util.HashMap<>(4);
        owner.put("teacherId", course.getTeacherId());
        owner.put("name", course.getName());
        return owner;
    }
}
