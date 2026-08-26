package com.lms.media.media.service.impl;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lms.common.domain.dto.PageDTO;
import com.lms.common.exceptions.CommonException;
import com.lms.common.exceptions.ForbiddenException;
import com.lms.common.utils.AssertUtils;
import com.lms.common.utils.BeanUtils;
import com.lms.common.utils.UserContext;
import com.lms.media.media.constants.MediaErrorInfo;
import com.lms.media.media.domain.po.Media;
import com.lms.media.media.domain.query.MediaPageQuery;
import com.lms.media.media.domain.vo.MediaVO;
import com.lms.media.media.enums.MediaType;
import com.lms.media.media.mapper.MediaMapper;
import com.lms.media.media.service.IMediaService;
import com.lms.media.media.storage.FileStorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 媒资业务服务实现
 *
 * 存储抽象：业务层只依赖 FileStorageService 接口，本地实现可换 OSS，替换不影响本类。
 * 事务说明：upload 不开启事务——文件已先落盘，DB 插入失败会残留孤儿文件，
 * 练手项目可接受；生产环境可引入补偿删除或定时清理任务。
 * 权限规则：查询/删除均限定当前登录用户本人（UserContext 来自网关透传的用户头）。
 */
@Service
@RequiredArgsConstructor
public class MediaServiceImpl implements IMediaService {

    /** 单个文件大小上限：100MB，超过直接拒绝，防止大文件拖垮内存与磁盘 */
    private static final long MAX_FILE_SIZE = 100L * 1024 * 1024;

    /** 图片扩展名白名单 */
    private static final Set<String> IMAGE_EXTS = Set.of("jpg", "jpeg", "png", "gif", "webp");

    /** 视频扩展名白名单 */
    private static final Set<String> VIDEO_EXTS = Set.of("mp4", "mov", "avi", "mkv");

    private final MediaMapper mediaMapper;
    private final FileStorageService fileStorageService;

    /** 访问 URL 前缀（如网关地址），来自 Nacos 配置 lms.media.url-prefix */
    @Value("${lms.media.url-prefix}")
    private String urlPrefix;

    @Override
    public MediaVO upload(MultipartFile file) {
        //1. 登录校验：未登录不允许上传，取当前用户 id
        Long userId = currentUserId();
        //2. 校验文件非空与大小：空文件拒绝，超过 100MB 拒绝
        AssertUtils.isTrue(file != null && !file.isEmpty(), "上传文件不能为空");
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new CommonException(MediaErrorInfo.FILE_TOO_LARGE);
        }
        //3. 按扩展名判定类型：白名单内归图片/视频，其余归其他，扩展名缺失或非法拒绝
        MediaType mediaType = classifyType(file.getOriginalFilename());
        //4. 调用存储抽象落盘：返回相对路径（yyyyMM/uuid.ext），本地实现可换 OSS
        String relativePath = fileStorageService.store(file);
        //5. 拼完整访问 URL：url-prefix + /uploads/ + 相对路径，与 WebConfig 静态映射对应
        String url = urlPrefix + "/uploads/" + relativePath;
        //6. 落库媒资记录：status=1 正常，归属当前用户
        Media media = new Media();
        media.setUserId(userId);
        media.setName(file.getOriginalFilename());
        media.setType(mediaType.getValue());
        media.setUrl(url);
        media.setSize(file.getSize());
        media.setMime(file.getContentType());
        media.setStatus(1);
        mediaMapper.insert(media);
        //7. 转 VO 返回
        return BeanUtils.copyBean(media, MediaVO.class);
    }

    @Override
    public PageDTO<MediaVO> queryMyPage(MediaPageQuery query) {
        //1. 登录校验并取当前用户 id
        Long userId = currentUserId();
        //2. 组装查询条件：固定按 user_id 过滤本人媒资，type 可选筛选
        LambdaQueryWrapper<Media> wrapper = new LambdaQueryWrapper<Media>()
                .eq(Media::getUserId, userId);
        if (query.getType() != null) {
            wrapper.eq(Media::getType, query.getType());
        }
        //3. 分页查询（默认按创建时间倒序）
        Page<Media> page = query.toMpPageDefaultSortByCreateTimeDesc();
        mediaMapper.selectPage(page, wrapper);
        //4. 转 VO 返回
        // 注意：PageDTO.of(Page<T>, List<T>) 要求 T 一致，Page<Media> 与 List<MediaVO> 类型不同，
        // 故使用其内部等价实现 of(Long, List)
        List<MediaVO> vos = BeanUtils.copyList(page.getRecords(), MediaVO.class);
        return PageDTO.of(page.getTotal(), vos);
    }

    @Override
    public MediaVO getDetail(Long id) {
        //1. 按 id 查询：不存在抛业务异常
        Media media = mediaMapper.selectById(id);
        if (media == null) {
            throw new CommonException(MediaErrorInfo.MEDIA_NOT_FOUND);
        }
        //2. 转 VO 返回
        return BeanUtils.copyBean(media, MediaVO.class);
    }

    @Override
    public void delete(Long id) {
        //1. 登录校验并取当前用户 id
        Long userId = currentUserId();
        //2. 查询记录：不存在抛业务异常
        Media media = mediaMapper.selectById(id);
        if (media == null) {
            throw new CommonException(MediaErrorInfo.MEDIA_NOT_FOUND);
        }
        //3. 权限校验：只能删除自己的媒资，防止越权删除他人文件
        if (!media.getUserId().equals(userId)) {
            throw new ForbiddenException("只能删除自己的媒资");
        }
        //4. 逻辑删除：BaseEntity.deleted 置 1，磁盘文件保留（生产可加异步清理任务）
        mediaMapper.deleteById(id);
    }

    /**
     * 校验登录并返回当前用户 id（未登录抛未授权异常）
     */
    private Long currentUserId() {
        Long userId = UserContext.getUser();
        AssertUtils.isNotNull(userId, "请先登录");
        return userId;
    }

    /**
     * 按扩展名判定媒资类型：白名单内归图片/视频，其余归其他；
     * 扩展名缺失或含非法字符（非字母数字）视为不支持的类型
     */
    private MediaType classifyType(String originalFilename) {
        String ext = FileUtil.extName(originalFilename);
        if (StrUtil.isBlank(ext) || !ext.matches("[a-zA-Z0-9]+")) {
            throw new CommonException(MediaErrorInfo.UNSUPPORTED_TYPE);
        }
        String lowerExt = ext.toLowerCase(Locale.ROOT);
        if (IMAGE_EXTS.contains(lowerExt)) {
            return MediaType.IMAGE;
        }
        if (VIDEO_EXTS.contains(lowerExt)) {
            return MediaType.VIDEO;
        }
        return MediaType.OTHER;
    }
}
