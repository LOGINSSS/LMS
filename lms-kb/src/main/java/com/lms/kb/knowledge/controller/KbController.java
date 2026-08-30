package com.lms.kb.knowledge.controller;

import com.lms.common.domain.R;
import com.lms.common.domain.dto.PageDTO;
import com.lms.common.exceptions.CommonException;
import com.lms.common.utils.UserContext;
import com.lms.kb.knowledge.constants.KbErrorInfo;
import com.lms.kb.knowledge.domain.dto.KbFormDTO;
import com.lms.kb.knowledge.domain.vo.DocVO;
import com.lms.kb.knowledge.domain.vo.KbVO;
import com.lms.kb.knowledge.service.IKbService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 知识库管理接口
 *
 * 业务模型（spec §4.4）：知识库归属 owner_type + owner_id——
 * 课程库走 /kb/course/...（兼容旧接口），个人库走 /kb/owner/2/{userId}/... 与 /kb/my（当前用户）。
 * 文档上传后异步入库。
 */
@Tag(name = "知识库管理接口")
@RestController
@RequestMapping("/kb")
@RequiredArgsConstructor
public class KbController {

    private final IKbService kbService;

    @PostMapping
    @Operation(summary = "创建知识库（默认课程库：owner_type=1；传 ownerType=2 为个人库）")
    public R<Long> createKb(@RequestBody @Valid KbFormDTO dto) {
        return R.ok(kbService.createKb(dto));
    }

    @GetMapping("/course/{courseId}")
    @Operation(summary = "按课程查询知识库")
    public R<KbVO> getByCourse(@PathVariable("courseId") Long courseId) {
        return R.ok(kbService.getByCourseId(courseId));
    }

    @PostMapping("/course/{courseId}/docs")
    @Operation(summary = "上传文档（课程知识库，触发异步入库管道）")
    public R<Long> uploadDoc(@PathVariable("courseId") Long courseId,
                             @RequestParam("file") MultipartFile file) {
        return R.ok(kbService.uploadDoc(courseId, file));
    }

    @DeleteMapping("/docs/{docId}")
    @Operation(summary = "删除文档（连带删除 ES 切片）")
    public R<Void> deleteDoc(@PathVariable("docId") Long docId) {
        kbService.deleteDoc(docId);
        return R.ok();
    }

    @GetMapping("/course/{courseId}/docs")
    @Operation(summary = "分页查询课程下的文档（含处理状态）")
    public R<PageDTO<DocVO>> queryDocs(@PathVariable("courseId") Long courseId,
                                       @RequestParam(value = "pageNo", defaultValue = "1") Integer pageNo,
                                       @RequestParam(value = "pageSize", defaultValue = "10") Integer pageSize) {
        return R.ok(kbService.queryDocs(courseId, pageNo, pageSize));
    }

    @DeleteMapping("/course/{courseId}")
    @Operation(summary = "删除知识库（级联删除文档与切片）")
    public R<Void> deleteKb(@PathVariable("courseId") Long courseId) {
        kbService.deleteKb(courseId);
        return R.ok();
    }

    // ============ 个人知识库（spec §4.4：owner_type + owner_id 维度） ============

    @GetMapping("/my")
    @Operation(summary = "我的个人知识库（owner_type=2 + 当前登录用户）")
    public R<KbVO> getMyKb() {
        return R.ok(kbService.getByOwner(2, requireUserId()));
    }

    @PostMapping("/my")
    @Operation(summary = "创建我的个人知识库（重复创建抛 KB_ALREADY_EXISTS）")
    public R<Long> createMyKb(@RequestParam(value = "name", required = false) String name) {
        KbFormDTO dto = new KbFormDTO();
        dto.setOwnerType(2);
        dto.setOwnerId(requireUserId());
        dto.setName(name);
        return R.ok(kbService.createKb(dto));
    }

    @PostMapping("/my/docs")
    @Operation(summary = "上传文档到我的个人知识库（触发异步入库管道）")
    public R<Long> uploadMyDoc(@RequestParam("file") MultipartFile file) {
        return R.ok(kbService.uploadDocByOwner(2, requireUserId(), file));
    }

    @GetMapping("/my/docs")
    @Operation(summary = "分页查询我的个人知识库文档")
    public R<PageDTO<DocVO>> queryMyDocs(@RequestParam(value = "pageNo", defaultValue = "1") Integer pageNo,
                                         @RequestParam(value = "pageSize", defaultValue = "10") Integer pageSize) {
        return R.ok(kbService.queryDocsByOwner(2, requireUserId(), pageNo, pageSize));
    }

    @DeleteMapping("/my")
    @Operation(summary = "删除我的个人知识库（级联删除文档与切片）")
    public R<Void> deleteMyKb() {
        kbService.deleteKbByOwner(2, requireUserId());
        return R.ok();
    }

    /** 按归属（ownerType/ownerId）操作个人/课程知识库的通用入口 */
    @GetMapping("/owner/{ownerType}/{ownerId}")
    @Operation(summary = "按归属查询知识库（ownerType: 1课程 2用户）")
    public R<KbVO> getByOwner(@PathVariable("ownerType") Integer ownerType,
                              @PathVariable("ownerId") Long ownerId) {
        return R.ok(kbService.getByOwner(ownerType, ownerId));
    }

    @PostMapping("/owner/{ownerType}/{ownerId}/docs")
    @Operation(summary = "按归属上传文档")
    public R<Long> uploadDocByOwner(@PathVariable("ownerType") Integer ownerType,
                                    @PathVariable("ownerId") Long ownerId,
                                    @RequestParam("file") MultipartFile file) {
        return R.ok(kbService.uploadDocByOwner(ownerType, ownerId, file));
    }

    @GetMapping("/owner/{ownerType}/{ownerId}/docs")
    @Operation(summary = "按归属分页查询文档")
    public R<PageDTO<DocVO>> queryDocsByOwner(@PathVariable("ownerType") Integer ownerType,
                                              @PathVariable("ownerId") Long ownerId,
                                              @RequestParam(value = "pageNo", defaultValue = "1") Integer pageNo,
                                              @RequestParam(value = "pageSize", defaultValue = "10") Integer pageSize) {
        return R.ok(kbService.queryDocsByOwner(ownerType, ownerId, pageNo, pageSize));
    }

    @DeleteMapping("/owner/{ownerType}/{ownerId}")
    @Operation(summary = "按归属删除知识库")
    public R<Void> deleteKbByOwner(@PathVariable("ownerType") Integer ownerType,
                                   @PathVariable("ownerId") Long ownerId) {
        kbService.deleteKbByOwner(ownerType, ownerId);
        return R.ok();
    }

    private Long requireUserId() {
        Long userId = UserContext.getUser();
        if (userId == null) {
            throw new CommonException(KbErrorInfo.KB_NOT_FOUND.getCode(), "未登录，无法定位个人知识库");
        }
        return userId;
    }
}
