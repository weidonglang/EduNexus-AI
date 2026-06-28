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
import weidonglang.tianshiwebside.common.error.BusinessException;
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

    @GetMapping("/templates")
    public ApiResponse<List<SafetyTemplate>> templates() {
        return ApiResponse.success(List.of(
                new SafetyTemplate("STRICT", "严格模式", "答辩演示、生产安全优先场景", true, "block",
                        "高风险直接 BLOCK；中低风险也进入阻断策略；全部写 moderation log。", true, true),
                new SafetyTemplate("BALANCED", "平衡模式", "日常教学业务默认推荐", true, "warn",
                        "高风险由敏感词等级和策略触发；中低风险 WARN；全部写日志并允许人工排查。", false, true),
                new SafetyTemplate("RELAXED", "宽松模式", "内网低风险演示环境", true, "review",
                        "命中内容进入 REVIEW，不直接中断主要流程，保留人工复核线索。", true, true),
                new SafetyTemplate("LOG_ONLY", "仅记录模式", "排查误杀或观察期", true, "log_only",
                        "不拦截用户请求，所有命中只写日志，便于观察策略影响。", false, true),
                new SafetyTemplate("DISABLED", "关闭模式", "临时排障，不建议长期使用", false, "log_only",
                        "不执行策略拦截，仅返回 DISABLED 结果。", false, false)
        ));
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
    public ApiResponse<SafetyTestResponse> test(
            Principal principal,
            @Valid @RequestBody SafetyTestRequest request
    ) {
        String operator = principal == null ? "admin" : principal.getName();
        try {
            ContentModerationService.ModerationResult result = moderationService.checkConfigured(request.scene(), request.content(), operator);
            boolean blocked = "BLOCK".equals(result.action());
            return ApiResponse.success(new SafetyTestResponse(
                    !blocked,
                    blocked,
                    result.scene(),
                    result.riskLevel(),
                    result.action(),
                    result.matchedWords(),
                    blocked ? "安全审查已拦截测试内容" : "安全审查测试完成：" + result.action(),
                    blocked ? "请检查敏感词、策略模板或改用人工复核模式" : "当前模板可按预期执行",
                    Instant.now()
            ));
        } catch (BusinessException ex) {
            return ApiResponse.success(new SafetyTestResponse(
                    false,
                    true,
                    request.scene(),
                    "HIGH",
                    "BLOCK",
                    "",
                    ex.getMessage(),
                    "严格模式命中高风险内容会阻断；如需观察不阻断，请使用仅记录模式",
                    Instant.now()
            ));
        }
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

    public record SafetyTemplate(
            String code,
            String name,
            String scenario,
            boolean enabled,
            String strategy,
            String description,
            boolean manualReview,
            boolean moderationLog
    ) {
    }

    public record SafetyTestResponse(
            boolean success,
            boolean blocked,
            String scene,
            String riskLevel,
            String action,
            String matchedWords,
            String message,
            String suggestion,
            Instant checkedAt
    ) {
    }
}
