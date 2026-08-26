package com.lms.media.media.storage.impl;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import com.lms.common.exceptions.CommonException;
import com.lms.media.media.constants.MediaErrorInfo;
import com.lms.media.media.storage.FileStorageService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * 本地磁盘文件存储实现
 *
 * 存储规则：根目录（lms.media.upload-dir）下按月份分目录（yyyyMM），
 * 文件名 uuid + 原扩展名，避免重名与路径猜测；返回相对路径供业务层拼 URL。
 * 说明：当前为默认实现，替换 OSS 时只需新增实现类并保证返回相对路径约定不变。
 */
@Slf4j
@Service
public class LocalFileStorageService implements FileStorageService {

    /** 存储根目录（本地磁盘绝对路径，来自 Nacos 配置 lms.media.upload-dir） */
    @Value("${lms.media.upload-dir}")
    private String uploadDir;

    /** 月份目录格式：yyyyMM，如 202608 */
    private static final DateTimeFormatter MONTH_FORMAT = DateTimeFormatter.ofPattern("yyyyMM");

    @Override
    public String store(MultipartFile file) {
        //1. 组装文件名与相对路径：uuid + 原扩展名，按月分目录
        String ext = FileUtil.extName(file.getOriginalFilename());
        String fileName = IdUtil.fastSimpleUUID() + (StrUtil.isBlank(ext) ? "" : "." + ext);
        String monthDir = LocalDate.now().format(MONTH_FORMAT);
        String relativePath = monthDir + "/" + fileName;
        //2. 写入磁盘：目录不存在则创建，目标文件覆盖写入
        Path targetDir = Paths.get(uploadDir, monthDir);
        try {
            Files.createDirectories(targetDir);
            Files.copy(file.getInputStream(), targetDir.resolve(fileName), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            // 磁盘写入失败统一转业务异常，由全局异常处理器返回统一响应
            log.error("文件写入存储失败，相对路径: {}", relativePath, e);
            throw new CommonException(MediaErrorInfo.UPLOAD_FAILED);
        }
        //3. 返回相对路径，完整访问 URL 由业务层拼接
        return relativePath;
    }
}
