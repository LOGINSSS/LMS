package com.lms.kb.knowledge.domain.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.lms.common.domain.po.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 知识库实体（个人 Agent 体系扩展后：归属 owner_type + owner_id）
 *
 * 对应表 knowledge_base。业务模型（spec §4.4 扩展点）：
 * - 课程知识库：owner_type=1 + owner_id=课程 id（course_id 语义保留，兼容旧数据）
 * - 个人知识库：owner_type=2 + owner_id=用户 id（course_id 为 NULL）
 * 唯一约束 uk(owner_type, owner_id)；知识库是文档与切片的容器，切片本体存 ES
 * （lms_kb_chunk 索引，带 owner 过滤字段）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("knowledge_base")
public class KnowledgeBase extends BaseEntity {

    /** 知识库 id（自增主键） */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 归属类型：1 课程 / 2 用户（个人知识库，spec §4.4） */
    private Integer ownerType;

    /** 归属 id：ownerType=1 时为课程 id；ownerType=2 时为用户 id */
    private Long ownerId;

    /** 课程 id（ownerType=1 时的冗余字段，保留兼容旧数据/旧接口） */
    private Long courseId;

    /** 知识库名称（默认取课程名） */
    private String name;

    /** 状态：0 禁用 / 1 可用 */
    private Integer status;

    /** 文档数量（含处理中） */
    private Integer docCount;

    /** 已入库切片总数 */
    private Integer chunkCount;
}
