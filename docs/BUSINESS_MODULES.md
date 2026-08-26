# 六大业务模块设计蓝图

本文档是媒资/搜索/学习/考试/数据中心/评价互动六个模块的设计依据。
每个模块遵循项目统一规范：独立库 `lms_<domain>`、配置进 Nacos、code-comment-style 注释、E2E 验证后提交。
实现顺序按依赖与独立性分批：第一批 media + remark（独立）；第二批 search + exam；第三批 learning；第四批 statistics。

## 1. 媒资模块（lms-media）

- 库：`lms_media`；端口：8088
- 职责：文件/视频上传与媒资管理（图片/视频/其他），统一存储抽象（本地磁盘，可扩展 OSS）
- 表：
  - `media`：id、user_id（上传者）、name（原文件名）、type（1图片/2视频/3其他）、url、size（字节）、mime、status、create_time、update_time、deleted
- 接口：
  - POST /medias/upload（multipart）→ MediaVO{id,url,name,type,size}（登录）
  - GET /medias/page → 分页（我的媒资）
  - GET /medias/{id} → 详情
  - DELETE /medias/{id} → 删除（仅本人）
  - 静态访问：/uploads/** 映射本地存储目录
- 依赖：无（独立）；课程封面/视频可引用其 url

## 2. 评价互动模块（lms-remark）

- 库：`lms_remark`；端口：8090
- 职责：跨业务对象（课程/笔记/问答）的通用点赞互动，记录 LikedRecord
- 表：
  - `liked_record`：id、user_id（点赞人）、biz_type（对象类型 1课程/2笔记/3问答）、biz_id（对象 id）、status（1已赞/0取消）、create_time、update_time、deleted；唯一键 uk_user_biz(user_id,biz_type,biz_id)
- 接口：
  - POST /likes/{bizType}/{bizId} → 切换点赞状态，返回 {liked, likeCount}（登录）
  - GET /likes/count/{bizType}/{bizId} → 点赞数
  - GET /likes/status/{bizType}/{bizId} → 当前用户是否已赞
  - GET /likes/statuses/{bizType}?bizIds=1,2,3 → 批量已赞状态（列表页用）
- 依赖：无；被课程/笔记/问答模块按 bizType 复用

## 3. 搜索模块（lms-search）

- 库：无（数据在 ES）；端口：8091
- 职责：基于 Elasticsearch 的课程搜索（keyword/分类）、课程推荐、兴趣标签
- 索引：`course`（es_doc：id、name、intro、category、teacherName、price、cover、createTime、score）
- 表（兴趣标签持久化）：
  - `user_interests`：id、user_id、tag（兴趣标签，如分类）、weight（权重，点击/选课累加）、create_time、update_time、deleted；唯一键 uk_user_tag
- 接口：
  - GET /search/courses?keyword=&category=&pageNo=&pageSize= → ES 搜索分页
  - GET /search/recommend?size=10 → 按兴趣标签推荐课程
  - GET /interests → 我的兴趣标签
  - POST /interests/record（选课/浏览时上报 tag，累加权重）
- 依赖：课程数据（从 lms-course 经 Feign 同步索引或查询时按 id 批量取数）

## 4. 考试/题库模块（lms-exam）

- 库：`lms_exam`；端口：8092
- 职责：题目管理（单选/多选/判断）、题库业务（题目按业务归类）
- 表：
  - `question`：id、name（题干）、type（1单选/2多选/3判断）、category（分类）、difficulty（1易/2中/3难）、analysis（解析）、answer（答案 JSON）、status、create_time、update_time、deleted
  - `question_biz`：id、question_id、biz_id（业务 id，如课程/考试 id）、score（该业务下分值）、create_time、update_time、deleted；唯一键 uk_question_biz
- 接口：
  - POST /admin/questions（教师建题）、PUT /admin/questions/{id}、DELETE /admin/questions/{id}
  - GET /admin/questions/page（按类型/分类筛选）
  - GET /questions/biz/{bizId} → 某业务下的题目列表（考试/练习用）
- 依赖：无（独立）；后续考试编排可引 question_biz

## 5. 学习过程模块（lms-learning）

- 库：`lms_learning`；端口：8093
- 职责：学习课次、学习记录、笔记（Note）、互动问答（提问/回答）、积分榜、积分记录、签到
- 表：
  - `lesson`：id、course_id、name（课次名）、media_id（关联媒资视频）、sort、create_time、update_time、deleted
  - `learning_record`：id、user_id、course_id、lesson_id、progress（进度百分比）、last_learn_time、create_time、update_time、deleted；唯一键 uk_user_lesson
  - `note`：id、user_id、course_id、lesson_id、content（笔记内容）、create_time、update_time、deleted
  - `question`（互动问答）：id、user_id、course_id、title、content、create_time、update_time、deleted
  - `answer`：id、question_id、user_id、content、accepted（是否采纳）、create_time、update_time、deleted
  - `points_record`：id、user_id、type（积分类型：1签到/2学习/3提问/4回答采纳/5点赞...）、points（增减分）、create_time、deleted
  - `sign_in`：id、user_id、sign_date（签到日期）、create_time、deleted；唯一键 uk_user_date
  - 积分榜：points_record 聚合（不建表，查询时 SUM）
- 接口：
  - GET /courses/{courseId}/lessons（课次列表）、GET /lessons/{id}（课次详情）
  - POST /learn/records（上报学习进度，幂等合并）、GET /courses/{courseId}/progress（课程进度）
  - POST /notes、GET /notes?courseId=（按课程查笔记）、PUT /notes/{id}、DELETE /notes/{id}
  - POST /questions（提问）、POST /questions/{id}/answers（回答）、GET /questions?courseId=（问题列表含回答）
  - POST /sign-in（签到，一天一次，积分+签到分）
  - GET /points/records（我的积分明细）、GET /points/board（积分榜 Top N）
- 依赖：lms-course（课次挂课程）、lms-media（课次视频）、lms-remark（笔记/问答点赞）

## 6. 数据中心（lms-statistics）

- 库：`lms_statistics`；端口：8094
- 职责：数据看板、今日数据、Top10 排行榜等统计（跨域数据每日汇总或实时聚合）
- 表：
  - `daily_stats`：id、stat_date（统计日期）、user_count（新增用户）、course_count（新增课程）、learn_count（学习人次）、enroll_count（选课人次）、create_time、deleted；唯一键 uk_date
  - `top_stats`（可选）：榜单快照（课程热度/学习榜），或实时聚合
- 接口：
  - GET /dashboard → 数据看板（总览：用户数/课程数/选课数/笔记数等）
  - GET /dashboard/today → 今日数据（今日新增用户/课程/学习人次/签到数）
  - GET /dashboard/top/courses?size=10 → 热门课程 Top10（按选课/学习量）
  - GET /dashboard/top/points?size=10 → 积分榜 Top10
- 依赖：lms-auth（用户数）、lms-course（课程/选课）、lms-learning（学习/签到/积分）——跨服务 Feign 聚合或接收 MQ 事件
