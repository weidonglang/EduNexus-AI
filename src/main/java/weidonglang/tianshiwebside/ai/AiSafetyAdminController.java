package weidonglang.tianshiwebside.ai;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import weidonglang.tianshiwebside.audit.AuditLogService;
import weidonglang.tianshiwebside.common.api.ApiResponse;
import weidonglang.tianshiwebside.common.api.PageResponse;
import weidonglang.tianshiwebside.common.api.Pagination;
import weidonglang.tianshiwebside.common.trace.TraceIdHolder;
import weidonglang.tianshiwebside.governance.ContentModerationService;
import weidonglang.tianshiwebside.governance.GovernanceController;

import java.security.Principal;
import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/api/admin/ai/safety")
@PreAuthorize("hasRole('ADMIN')")
public class AiSafetyAdminController {
    private final ContentModerationService moderationService;
    private final JdbcTemplate jdbcTemplate;
    private final AuditLogService auditLogService;

    public AiSafetyAdminController(
            ContentModerationService moderationService,
            JdbcTemplate jdbcTemplate,
            AuditLogService auditLogService
    ) {
        this.moderationService = moderationService;
        this.jdbcTemplate = jdbcTemplate;
        this.auditLogService = auditLogService;
    }

    @GetMapping("/config")
    public ApiResponse<List<ContentModerationService.SafetyConfig>> config() {
        return ApiResponse.success(moderationService.safetyConfigs());
    }

    @PutMapping("/config")
    public ApiResponse<List<ContentModerationService.SafetyConfig>> updateConfig(
            Principal principal,
            @Valid @RequestBody SafetyConfigUpdateRequest request
    ) {
        for (SafetyConfigItem item : request.configs()) {
            moderationService.updateSafetyConfig(item.scene(), item.enabled(), item.strategy(), item.description());
            auditLogService.record(principal.getName(), "UPDATE_AI_SAFETY_CONFIG", "AI_SAFETY",
                    item.scene(), item.strategy() + ", enabled=" + item.enabled(), TraceIdHolder.get());
        }
        return ApiResponse.success(moderationService.safetyConfigs());
    }

    @PostMapping("/test")
    public ApiResponse<ContentModerationService.ModerationResult> test(
            Principal principal,
            @Valid @RequestBody SafetyTestRequest request
    ) {
        String operator = principal == null ? "admin" : principal.getName();
        return ApiResponse.success(moderationService.checkConfigured(request.scene(), request.content(), operator));
    }

    @GetMapping("/hits")
    public ApiResponse<PageResponse<GovernanceController.ModerationLogRow>> hits(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String scene
    ) {
        int safePage = Pagination.safePage(page);
        int safeSize = Pagination.safeSize(size);
        String normalizedScene = scene == null || scene.isBlank() ? null : scene.trim().toUpperCase();
        List<GovernanceController.ModerationLogRow> rows = jdbcTemplate.query("""
                        select id, scene, content_hash, matched_words, risk_level, action, operator, trace_id, created_at
                        from content_moderation_log
                        where (matched_words is not null and matched_words <> '')
                          and (? is null or scene = ?)
                        order by created_at desc
                        limit ? offset ?
                        """,
                (rs, rowNum) -> new GovernanceController.ModerationLogRow(
                        rs.getLong("id"),
                        rs.getString("scene"),
                        rs.getString("content_hash"),
                        rs.getString("matched_words"),
                        rs.getString("risk_level"),
                        rs.getString("action"),
                        rs.getString("operator"),
                        rs.getString("trace_id"),
                        rs.getObject("created_at", Instant.class)
                ),
                normalizedScene,
                normalizedScene,
                safeSize,
                Pagination.offset(safePage, safeSize)
        );
        Long total = jdbcTemplate.queryForObject("""
                        select count(*)
                        from content_moderation_log
                        where (matched_words is not null and matched_words <> '')
                          and (? is null or scene = ?)
                        """,
                Long.class,
                normalizedScene,
                normalizedScene
        );
        return ApiResponse.success(new PageResponse<>(rows, safePage, safeSize, total == null ? 0 : total));
    }

    public record SafetyConfigUpdateRequest(@NotNull List<@Valid SafetyConfigItem> configs) {
    }

    public record SafetyConfigItem(
            @NotBlank @Size(max = 80) String scene,
            @NotNull Boolean enabled,
            @NotBlank @Size(max = 20) String strategy,
            @Size(max = 300) String description
    ) {
    }

    public record SafetyTestRequest(
            @NotBlank @Size(max = 80) String scene,
            @NotBlank @Size(max = 4000) String content
    ) {
    }
}
