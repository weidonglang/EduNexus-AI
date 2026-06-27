package weidonglang.tianshiwebside.notice;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import weidonglang.tianshiwebside.audit.AuditLogService;
import weidonglang.tianshiwebside.common.api.ApiResponse;
import weidonglang.tianshiwebside.common.cache.QueryCacheService;
import weidonglang.tianshiwebside.governance.ContentModerationService;
import weidonglang.tianshiwebside.notice.mapper.NoticeMapper;

import java.security.Principal;
import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/api/admin/notices")
@PreAuthorize("hasAuthority('NOTICE_WRITE')")
public class AdminNoticeController {
    private final NoticeMapper noticeMapper;
    private final NotificationService notificationService;
    private final AuditLogService auditLogService;
    private final QueryCacheService queryCacheService;
    private final ContentModerationService moderationService;

    public AdminNoticeController(
            NoticeMapper noticeMapper,
            NotificationService notificationService,
            AuditLogService auditLogService,
            QueryCacheService queryCacheService,
            ContentModerationService moderationService
    ) {
        this.noticeMapper = noticeMapper;
        this.notificationService = notificationService;
        this.auditLogService = auditLogService;
        this.queryCacheService = queryCacheService;
        this.moderationService = moderationService;
    }

    @PostMapping
    /**
     * 功能：发布通知公告。
     * 说明：管理员填写公告标题、内容、类型和接收角色后，后端保存公告记录，
     * 并为目标用户生成通知，用于首页公告、选课通知、考试通知和审核通知展示。
     */
    public ApiResponse<NoticeMapper.NoticeRow> publish(Principal principal, @Valid @RequestBody PublishNoticeRequest request) {
        moderationService.checkConfigured("NOTICE", request.title() + "\n" + request.content(), principal.getName());
        NoticeMapper.NoticeCommand command = new NoticeMapper.NoticeCommand(
                request.title().trim(), request.content().trim(), request.category().trim(), request.pinned(),
                Instant.now(), principal.getName());
        noticeMapper.insertNotice(command);
        List<Long> userIds = request.roleCode() == null || request.roleCode().isBlank()
                ? noticeMapper.findAllUserIds()
                : noticeMapper.findUserIdsByRoleCode(request.roleCode().trim());
        notificationService.notifyUsers(userIds, request.title(), request.content(), request.category(), "NOTICE", command.getId());
        auditLogService.record(principal.getName(), "PUBLISH_NOTICE", "NOTICE", command.getId(), request.title(), null);
        queryCacheService.evictByPrefix("query:notices:");
        queryCacheService.evictByPrefix("query:notifications:");
        queryCacheService.evictByPrefix("query:dashboard:");
        return ApiResponse.success(noticeMapper.findNotices(null, 1, 0).get(0));
    }

    @GetMapping("/stats")
    /**
     * 功能：查询公告统计数据。
     * 说明：管理端公告页面用于展示不同类别公告数量和发布情况，辅助答辩说明公告管理功能完整。
     */
    public ApiResponse<List<NoticeMapper.NoticeStatRow>> stats() {
        return ApiResponse.success(noticeMapper.findNoticeStats());
    }

    public record PublishNoticeRequest(@NotBlank String title, @NotBlank String content, @NotBlank String category,
                                       @NotNull Boolean pinned, String roleCode) {
    }
}
