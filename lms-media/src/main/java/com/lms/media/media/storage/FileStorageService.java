package com.lms.media.media.storage;

import org.springframework.web.multipart.MultipartFile;

/**
 * 文件存储抽象
 *
 * 职责：把上传文件写入存储介质，返回可访问的相对路径（如 202608/xxx.jpg），
 * 完整访问 URL 由业务层拼接 url-prefix 得到。
 * 设计：默认本地磁盘实现（LocalFileStorageService），后续可替换为 OSS 等对象存储，
 * 业务层只依赖本接口，不感知具体存储介质。
 */
public interface FileStorageService {

    /**
     * 存储上传文件
     *
     * @param file 上传的 multipart 文件
     * @return 存储相对路径（目录/文件名，如 202608/xxx.jpg）
     */
    String store(MultipartFile file);
}
