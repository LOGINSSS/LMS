package com.lms.kb.knowledge.service;

import com.lms.common.domain.dto.PageDTO;
import com.lms.kb.knowledge.domain.dto.KbFormDTO;
import com.lms.kb.knowledge.domain.vo.DocVO;
import com.lms.kb.knowledge.domain.vo.KbVO;
import org.springframework.web.multipart.MultipartFile;

/**
 * 知识库管理服务（spec §4.4：归属 owner_type + owner_id 扩展后）
 *
 * 职责：知识库 CRUD（课程库/个人库）、文档上传/删除/分页查询。
 * 课程库：ownerType=1 + courseId（兼容旧接口）；个人库：ownerType=2 + userId。
 * 文档上传后落库（待解析）并异步触发入库管道（DocPipelineService）。
 */
public interface IKbService {

    /** 创建知识库（owner_type=1 课程库按 course_id 唯一；owner_type=2 个人库按 owner_id 唯一） */
    Long createKb(KbFormDTO dto);

    /** 按课程查知识库（不存在抛 KB_NOT_FOUND） */
    KbVO getByCourseId(Long courseId);

    /** 按归属查知识库（spec §4.4：个人知识库） */
    KbVO getByOwner(Integer ownerType, Long ownerId);

    /** 上传文档（课程知识库）：校验类型 → 落盘 → 落库(待解析) → 异步触发入库管道，返回文档 id */
    Long uploadDoc(Long courseId, MultipartFile file);

    /** 上传文档（按归属，个人知识库走 ownerType=2） */
    Long uploadDocByOwner(Integer ownerType, Long ownerId, MultipartFile file);

    /** 删除文档：逻辑删 + 删 ES 切片 + 删本地文件 */
    void deleteDoc(Long docId);

    /** 分页查询课程下的文档 */
    PageDTO<DocVO> queryDocs(Long courseId, Integer pageNo, Integer pageSize);

    /** 分页查询归属下的文档 */
    PageDTO<DocVO> queryDocsByOwner(Integer ownerType, Long ownerId, Integer pageNo, Integer pageSize);

    /** 删除知识库（课程库）：逻辑删 + 级联删文档 + 删 ES 切片 */
    void deleteKb(Long courseId);

    /** 删除知识库（按归属，个人库） */
    void deleteKbByOwner(Integer ownerType, Long ownerId);

    /**
     * 课程正文文本同步入知识库（spec 0.2 §8.1：课程发布后一键同步章节正文）
     *
     * 把课程目录+章节 markdown 拼成一份文本，落为 .md 文档走入库管道。
     *
     * @param courseId 课程 id
     * @param mdText   拼接后的 markdown 全文
     * @return 文档 id
     */
    Long syncCourseText(Long courseId, String mdText);
}
