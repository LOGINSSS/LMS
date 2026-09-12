package com.lms.learning.learning.controller;

import com.lms.common.domain.R;
import com.lms.common.domain.dto.PageDTO;
import com.lms.learning.learning.domain.vo.NotificationVO;
import com.lms.learning.learning.service.NotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 站内消息（信箱）接口：本人未读列表 / 已读管理
 *
 * 挂在 /qa/notifications 前缀下复用网关 lms-learning 路由（/qa/**），无需新增网关配置。
 */
@Tag(name = "站内消息接口（信箱）")
@RestController
@RequestMapping("/qa/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    /** 我的消息分页（含已读/未读，倒序） */
    @GetMapping("/page")
    @Operation(summary = "我的消息分页")
    public R<PageDTO<NotificationVO>> page(
            @RequestParam(value = "pageNo", defaultValue = "1") Integer pageNo,
            @RequestParam(value = "pageSize", defaultValue = "20") Integer pageSize) {
        return R.ok(notificationService.pageMy(pageNo, pageSize));
    }

    /** 我的未读数（顶栏信箱红点） */
    @GetMapping("/unread-count")
    @Operation(summary = "我的未读消息数")
    public R<Integer> unreadCount() {
        return R.ok(notificationService.countUnread());
    }

    /** 标记已读（本人，支持逗号分隔多 id） */
    @PutMapping("/read")
    @Operation(summary = "标记已读")
    public R<Void> markRead(@RequestParam("ids") List<Long> ids) {
        notificationService.markRead(ids);
        return R.ok();
    }

    /** 全部标记已读（本人） */
    @PutMapping("/read-all")
    @Operation(summary = "全部标记已读")
    public R<Void> markAllRead() {
        notificationService.markAllRead();
        return R.ok();
    }
}
