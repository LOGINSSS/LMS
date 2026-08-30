package com.lms.kb.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 知识库业务配置（前缀 lms.kb，对应 application.yml / nacos lms-kb.yaml）
 */
@Data
@ConfigurationProperties(prefix = "lms.kb")
public class KbProperties {

    /** ES 切片索引名 */
    private String esIndex = "lms_kb_chunk";

    /** embedding 向量维度（DashScope text-embedding-v3 = 1024） */
    private int denseDim = 1024;

    /** 混合检索每路候选数 */
    private int fetchK = 20;

    /** 精排后保留条数 */
    private int rerankTopK = 5;

    /** RRF 融合平滑常数 */
    private int rrfK = 60;

    /** 兜底分片大小（字符） */
    private int chunkSize = 900;

    /** 兜底分片重叠（字符） */
    private int chunkOverlap = 100;

    /** embedding 单次请求批量上限（DashScope 限制 10） */
    private int embedBatchSize = 10;

    /** 文档解析管道线程数 */
    private int pipelineThreads = 4;

    /** ES text 字段分词器（装 ik 插件后可配 ik_smart） */
    private String analyzer = "standard";

    /** 上传文件落盘目录 */
    private String dataDir = System.getProperty("java.io.tmpdir") + "/lms-kb";
}
