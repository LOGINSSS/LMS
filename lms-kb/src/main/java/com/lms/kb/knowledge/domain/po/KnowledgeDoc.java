package com.lms.kb.knowledge.domain.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.lms.common.domain.po.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 知识库文档实体
 *
 * 对应表 knowledge_doc。一条记录 = 一次上传的文档（可能解析出多个切片，切片存 ES）。
 * 处理状态机：0待解析 → 1解析中 → 2向量化中 → 3完成；任一步失败置 4 并记录 error_msg。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("knowledge_doc")
public class KnowledgeDoc extends BaseEntity {

    /** 文档 id（自增主键） */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 知识库 id（knowledge_base.id） */
    private Long kbId;

    /** 归属类型：1 课程 / 2 用户（冗余自知识库，切片过滤用，spec §4.4） */
    private Integer ownerType;

    /** 归属 id（冗余自知识库） */
    private Long ownerId;

    /** 课程 id（ownerType=1 时冗余，检索过滤用） */
    private Long courseId;

    /** 展示用文件名（含扩展名） */
    private String fileName;

    /** 文件类型：md/txt/docx/pptx/pdf/png/jpg/... */
    private String fileType;

    /** lms-media 文件 id（若走媒资服务） */
    private Long fileId;

    /** 入库切片数 */
    private Integer chunkCount;

    /** 处理状态：0待解析 1解析中 2向量化中 3完成 4失败 */
    private Integer status;

    /** 失败原因 */
    private String errorMsg;
}
