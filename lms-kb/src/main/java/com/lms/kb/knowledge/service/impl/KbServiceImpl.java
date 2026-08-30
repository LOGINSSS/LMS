package com.lms.kb.knowledge.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lms.common.domain.dto.PageDTO;
import com.lms.common.exceptions.CommonException;
import com.lms.common.utils.UserContext;
import com.lms.kb.config.KbProperties;
import com.lms.kb.ingest.DocPipelineService;
import com.lms.kb.knowledge.constants.KbErrorInfo;
import com.lms.kb.knowledge.domain.dto.KbFormDTO;
import com.lms.kb.knowledge.domain.po.KnowledgeBase;
import com.lms.kb.knowledge.domain.po.KnowledgeDoc;
import com.lms.kb.knowledge.domain.vo.DocVO;
import com.lms.kb.knowledge.domain.vo.KbVO;
import com.lms.kb.knowledge.mapper.KnowledgeBaseMapper;
import com.lms.kb.knowledge.mapper.KnowledgeDocMapper;
import com.lms.kb.knowledge.service.IKbService;
import com.lms.kb.rag.store.EsKnowledge;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

/**
 * 知识库管理服务实现（spec §4.4：owner_type=1 课程库 / owner_type=2 个人库）
 *
 * 课程库兼容旧接口：courseId 语义保留，内部统一为 owner(1, courseId)；
 * 个人库：owner(2, userId)，文档与切片带 owner 归属，检索按 owner 隔离。
 */
@Service
@RequiredArgsConstructor
public class KbServiceImpl implements IKbService {

    /** 支持入库的文件类型（与 Python 版 ingest_file 一致） */
    private static final Set<String> SUPPORTED_TYPES = Set.of(
            "md", "markdown", "txt", "docx", "pptx", "pdf",
            "png", "jpg", "jpeg", "bmp", "webp");

    /** 归属类型：1 课程 / 2 用户（与 EsKnowledge.OWNER_* 一致） */
    public static final int OWNER_COURSE = 1;
    public static final int OWNER_USER = 2;

    private final KnowledgeBaseMapper kbMapper;
    private final KnowledgeDocMapper docMapper;
    private final KbProperties kbProperties;
    private final EsKnowledge esKnowledge;
    private final DocPipelineService docPipelineService;

    @Override
    @Transactional
    public Long createKb(KbFormDTO dto) {
        int ownerType = dto.getOwnerType() == null ? OWNER_COURSE : dto.getOwnerType();
        Long ownerId;
        if (ownerType == OWNER_USER) {
            // 个人知识库：ownerId 缺省取当前登录用户（网关 user-info 头 → UserContext）
            ownerId = dto.getOwnerId() != null ? dto.getOwnerId() : UserContext.getUser();
            if (ownerId == null) {
                throw new CommonException(KbErrorInfo.KB_NOT_FOUND.getCode(), "个人知识库需要用户 id");
            }
        } else {
            ownerType = OWNER_COURSE;
            ownerId = dto.getCourseId();
            if (ownerId == null) {
                throw new CommonException(KbErrorInfo.KB_NOT_FOUND.getCode(), "课程知识库需要课程 id");
            }
        }
        Long count = kbMapper.selectCount(ownerWrapper(ownerType, ownerId));
        if (count != null && count > 0) {
            throw new CommonException(KbErrorInfo.KB_ALREADY_EXISTS);
        }
        KnowledgeBase kb = new KnowledgeBase();
        kb.setOwnerType(ownerType);
        kb.setOwnerId(ownerId);
        kb.setCourseId(ownerType == OWNER_COURSE ? ownerId : null);
        kb.setName(StrUtil.blankToDefault(dto.getName(),
                ownerType == OWNER_COURSE ? "课程" + ownerId + "知识库" : "我的知识库"));
        kb.setStatus(1);
        kb.setDocCount(0);
        kb.setChunkCount(0);
        kbMapper.insert(kb);
        return kb.getId();
    }

    @Override
    public KbVO getByCourseId(Long courseId) {
        return toVO(getEntityByOwnerOrThrow(OWNER_COURSE, courseId));
    }

    @Override
    public KbVO getByOwner(Integer ownerType, Long ownerId) {
        return toVO(getEntityByOwnerOrThrow(ownerType, ownerId));
    }

    @Override
    public Long uploadDoc(Long courseId, MultipartFile file) {
        return uploadDocByOwner(OWNER_COURSE, courseId, file);
    }

    @Override
    @Transactional
    public Long uploadDocByOwner(Integer ownerType, Long ownerId, MultipartFile file) {
        KnowledgeBase kb = getEntityByOwnerOrThrow(ownerType, ownerId);
        if (kb.getStatus() == null || kb.getStatus() != 1) {
            throw new CommonException(KbErrorInfo.KB_DISABLED);
        }
        String fileName = file.getOriginalFilename();
        String ext = StrUtil.subAfter(fileName, ".", true).toLowerCase();
        if (!SUPPORTED_TYPES.contains(ext)) {
            throw new CommonException(KbErrorInfo.DOC_TYPE_UNSUPPORTED);
        }
        try {
            // 落盘：dataDir/{docId}.{ext}，docId 生成后写入
            KnowledgeDoc doc = new KnowledgeDoc();
            doc.setKbId(kb.getId());
            doc.setOwnerType(kb.getOwnerType());
            doc.setOwnerId(kb.getOwnerId());
            doc.setCourseId(kb.getCourseId());
            doc.setFileName(fileName);
            doc.setFileType(ext);
            doc.setStatus(0);
            doc.setChunkCount(0);
            docMapper.insert(doc);

            Path dir = Path.of(kbProperties.getDataDir());
            Files.createDirectories(dir);
            Path target = dir.resolve(doc.getId() + "." + ext);
            file.transferTo(target);

            // 触发异步入库管道
            docPipelineService.processDoc(doc.getId());

            kb.setDocCount(kb.getDocCount() + 1);
            kbMapper.updateById(kb);
            return doc.getId();
        } catch (CommonException e) {
            throw e;
        } catch (Exception e) {
            throw new CommonException(KbErrorInfo.DOC_PROCESS_FAILED.getCode(),
                    "文件保存失败: " + e.getMessage());
        }
    }

    @Override
    @Transactional
    public void deleteDoc(Long docId) {
        KnowledgeDoc doc = docMapper.selectById(docId);
        if (doc == null) {
            throw new CommonException(KbErrorInfo.DOC_NOT_FOUND);
        }
        // 删 ES 切片
        try {
            esKnowledge.deleteByDocId(String.valueOf(doc.getId()));
        } catch (Exception e) {
            throw new CommonException(KbErrorInfo.DOC_PROCESS_FAILED.getCode(),
                    "删除 ES 切片失败: " + e.getMessage());
        }
        // 删本地文件
        try {
            Files.deleteIfExists(Path.of(kbProperties.getDataDir(), doc.getId() + "." + doc.getFileType()));
        } catch (Exception ignored) {
        }
        docMapper.deleteById(docId);
        KnowledgeBase kb = kbMapper.selectById(doc.getKbId());
        if (kb != null && kb.getDocCount() > 0) {
            kb.setDocCount(kb.getDocCount() - 1);
            kbMapper.updateById(kb);
        }
    }

    @Override
    public PageDTO<DocVO> queryDocs(Long courseId, Integer pageNo, Integer pageSize) {
        return queryDocsByOwner(OWNER_COURSE, courseId, pageNo, pageSize);
    }

    @Override
    public PageDTO<DocVO> queryDocsByOwner(Integer ownerType, Long ownerId, Integer pageNo, Integer pageSize) {
        KnowledgeBase kb = getEntityByOwnerOrThrow(ownerType, ownerId);
        int no = pageNo == null ? 1 : pageNo;
        int size = pageSize == null ? 10 : pageSize;
        Page<KnowledgeDoc> page = docMapper.selectPage(new Page<>(no, size),
                new LambdaQueryWrapper<KnowledgeDoc>()
                        .eq(KnowledgeDoc::getKbId, kb.getId())
                        .orderByDesc(KnowledgeDoc::getId));
        List<DocVO> vos = page.getRecords().stream().map(d -> {
            DocVO vo = new DocVO();
            vo.setId(d.getId());
            vo.setFileName(d.getFileName());
            vo.setFileType(d.getFileType());
            vo.setChunkCount(d.getChunkCount());
            vo.setStatus(d.getStatus());
            vo.setErrorMsg(d.getErrorMsg());
            vo.setCreateTime(d.getCreateTime());
            return vo;
        }).toList();
        return PageDTO.of(page.getTotal(), vos);
    }

    @Override
    @Transactional
    public void deleteKb(Long courseId) {
        deleteKbByOwner(OWNER_COURSE, courseId);
    }

    @Override
    @Transactional
    public void deleteKbByOwner(Integer ownerType, Long ownerId) {
        KnowledgeBase kb = getEntityByOwnerOrThrow(ownerType, ownerId);
        // 删 ES 切片（按归属）
        try {
            esKnowledge.deleteByOwner(kb.getOwnerType(), kb.getOwnerId());
        } catch (Exception e) {
            throw new CommonException(KbErrorInfo.DOC_PROCESS_FAILED.getCode(),
                    "删除 ES 切片失败: " + e.getMessage());
        }
        // 逻辑删文档
        docMapper.delete(new LambdaQueryWrapper<KnowledgeDoc>().eq(KnowledgeDoc::getKbId, kb.getId()));
        // 逻辑删知识库
        kbMapper.deleteById(kb.getId());
    }

    /** 按归属查知识库实体，不存在抛 KB_NOT_FOUND */
    private KnowledgeBase getEntityByOwnerOrThrow(Integer ownerType, Long ownerId) {
        KnowledgeBase kb = kbMapper.selectOne(ownerWrapper(ownerType, ownerId));
        if (kb == null) {
            throw new CommonException(KbErrorInfo.KB_NOT_FOUND);
        }
        return kb;
    }

    /** 归属查询条件：课程库按 course_id（兼容旧数据），个人库按 owner_type+owner_id */
    private LambdaQueryWrapper<KnowledgeBase> ownerWrapper(Integer ownerType, Long ownerId) {
        LambdaQueryWrapper<KnowledgeBase> w = new LambdaQueryWrapper<>();
        if (ownerType != null && ownerType == OWNER_USER) {
            w.eq(KnowledgeBase::getOwnerType, OWNER_USER).eq(KnowledgeBase::getOwnerId, ownerId);
        } else {
            w.eq(KnowledgeBase::getCourseId, ownerId);
        }
        return w;
    }

    private KbVO toVO(KnowledgeBase kb) {
        KbVO vo = new KbVO();
        vo.setId(kb.getId());
        vo.setOwnerType(kb.getOwnerType());
        vo.setOwnerId(kb.getOwnerId());
        vo.setCourseId(kb.getCourseId());
        vo.setName(kb.getName());
        vo.setStatus(kb.getStatus());
        vo.setDocCount(kb.getDocCount());
        vo.setChunkCount(kb.getChunkCount());
        vo.setCreateTime(kb.getCreateTime());
        return vo;
    }
}
