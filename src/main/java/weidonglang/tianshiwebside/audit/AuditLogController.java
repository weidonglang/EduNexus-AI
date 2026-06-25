package weidonglang.tianshiwebside.audit;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import weidonglang.tianshiwebside.audit.mapper.AuditLogMapper;
import weidonglang.tianshiwebside.common.api.ApiResponse;
import weidonglang.tianshiwebside.common.api.PageResponse;

/**
 * 操作日志查询接口。
 *
 * 管理员可以查看用户、公告、成绩、考试等重要操作的审计记录。
 * 这个功能用于说明系统具有基础的安全审计能力，便于追踪谁在什么时候做了什么操作。
 */
@RestController
@RequestMapping("/api/admin/audit-logs")
@PreAuthorize("hasAuthority('AUDIT_READ')")
public class AuditLogController {
    private final AuditLogMapper auditLogMapper;

    public AuditLogController(AuditLogMapper auditLogMapper) {
        this.auditLogMapper = auditLogMapper;
    }

    /**
     * 分页查询审计日志。
     *
     * 支持关键词检索，避免日志量增大后一次性加载过多数据。
     */
    @GetMapping
    public ApiResponse<PageResponse<AuditLogMapper.AuditLogRow>> logs(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String riskLevel,
            @RequestParam(required = false) String module,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        int safePage = Math.max(page, 1);
        int safeSize = Math.min(Math.max(size, 1), 100);
        String normalizedKeyword = keyword == null || keyword.isBlank() ? null : "%" + keyword.trim() + "%";
        String normalizedRiskLevel = riskLevel == null || riskLevel.isBlank() ? null : riskLevel.trim().toUpperCase();
        String normalizedModule = module == null || module.isBlank() ? null : module.trim().toUpperCase();
        return ApiResponse.success(new PageResponse<>(
                auditLogMapper.findLogs(normalizedKeyword, normalizedRiskLevel, normalizedModule, safeSize, (safePage - 1) * safeSize),
                safePage,
                safeSize,
                auditLogMapper.countLogs(normalizedKeyword, normalizedRiskLevel, normalizedModule)
        ));
    }
}
