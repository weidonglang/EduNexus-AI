package weidonglang.tianshiwebside.admin;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import weidonglang.tianshiwebside.audit.AuditLogService;
import weidonglang.tianshiwebside.common.api.ApiResponse;
import weidonglang.tianshiwebside.common.api.PageResponse;
import weidonglang.tianshiwebside.common.api.Pagination;
import weidonglang.tianshiwebside.common.error.BusinessException;
import weidonglang.tianshiwebside.common.error.ErrorCode;

import java.security.Principal;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('ADMIN')")
public class BatchAndArchiveController {
    private final JdbcTemplate jdbcTemplate;
    private final AuditLogService auditLogService;

    public BatchAndArchiveController(JdbcTemplate jdbcTemplate, AuditLogService auditLogService) {
        this.jdbcTemplate = jdbcTemplate;
        this.auditLogService = auditLogService;
    }

    @GetMapping("/batch-tasks")
    public ApiResponse<PageResponse<BatchTaskRow>> batchTasks(
            @RequestParam(required = false) String taskType,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        int safePage = Pagination.safePage(page);
        int safeSize = Pagination.safeSize(size);
        String where = "where (? is null or task_type = ?) and (? is null or status = ?)";
        List<BatchTaskRow> rows = jdbcTemplate.query("""
                        select id, task_type, operator, started_at, ended_at, status, success_count, failure_count, failure_detail, report_path
                        from batch_task
                        %s
                        order by started_at desc
                        limit ? offset ?
                        """.formatted(where),
                (rs, rowNum) -> new BatchTaskRow(
                        rs.getLong("id"),
                        rs.getString("task_type"),
                        rs.getString("operator"),
                        rs.getTimestamp("started_at").toInstant(),
                        rs.getTimestamp("ended_at") == null ? null : rs.getTimestamp("ended_at").toInstant(),
                        rs.getString("status"),
                        rs.getInt("success_count"),
                        rs.getInt("failure_count"),
                        rs.getString("failure_detail"),
                        rs.getString("report_path")
                ),
                blankToNull(taskType), blankToNull(taskType), blankToNull(status), blankToNull(status),
                safeSize, Pagination.offset(safePage, safeSize));
        Long total = jdbcTemplate.queryForObject("select count(*) from batch_task " + where, Long.class,
                blankToNull(taskType), blankToNull(taskType), blankToNull(status), blankToNull(status));
        return ApiResponse.success(new PageResponse<>(rows, safePage, safeSize, total == null ? 0 : total));
    }

    @GetMapping("/data-archive/preview")
    public ApiResponse<ArchivePreview> archivePreview(
            @RequestParam String objectType,
            @RequestParam(required = false) String term
    ) {
        return ApiResponse.success(new ArchivePreview(objectType, term, countObject(objectType, term), true,
                "dry-run 仅统计，不改变数据库。当前学期和被引用数据不会被清理。"));
    }

    @PostMapping("/data-archive/archive")
    @Transactional
    public ApiResponse<ArchiveRecordRow> archive(
            Principal principal,
            @RequestParam String objectType,
            @RequestParam(required = false) String term,
            @RequestParam(defaultValue = "true") boolean dryRun
    ) {
        int count = countObject(objectType, term);
        ArchiveRecordRow row = insertArchiveRecord(principal, objectType, term, "ARCHIVE", dryRun, count,
                dryRun ? "dry-run preview" : "safe archive record only");
        insertBatchTask(principal, "ARCHIVE_" + objectType, count, 0, row.detail());
        auditLogService.record(username(principal), dryRun ? "ARCHIVE_DRY_RUN" : "ARCHIVE_RECORD", "DATA_ARCHIVE",
                row.id(), objectType + ", term=" + term + ", affected=" + count, null);
        return ApiResponse.success(row);
    }

    @PostMapping("/data-archive/cleanup")
    @Transactional
    public ApiResponse<ArchiveRecordRow> cleanup(
            Principal principal,
            @RequestParam String objectType,
            @RequestParam(required = false) String term,
            @RequestParam(defaultValue = "true") boolean dryRun
    ) {
        if (isCurrentTerm(term)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "不允许清理当前学期");
        }
        int count = countObject(objectType, term);
        ArchiveRecordRow row = insertArchiveRecord(principal, objectType, term, "CLEANUP", dryRun, count,
                dryRun ? "dry-run preview" : "cleanup task recorded; destructive delete is intentionally disabled in demo mode");
        insertBatchTask(principal, "CLEANUP_" + objectType, dryRun ? 0 : count, 0, row.detail());
        auditLogService.record(username(principal), dryRun ? "CLEANUP_DRY_RUN" : "CLEANUP_RECORDED", "DATA_ARCHIVE",
                row.id(), objectType + ", term=" + term + ", affected=" + count, null);
        return ApiResponse.success(row);
    }

    @GetMapping(value = "/data-archive/export.csv", produces = "text/csv;charset=UTF-8")
    public ResponseEntity<String> exportArchiveRecords() {
        List<ArchiveRecordRow> rows = jdbcTemplate.query("""
                        select id, object_type, term, action, dry_run, affected_count, operator, detail, created_at
                        from data_archive_record
                        order by created_at desc
                        """,
                (rs, rowNum) -> new ArchiveRecordRow(
                        rs.getLong("id"),
                        rs.getString("object_type"),
                        rs.getString("term"),
                        rs.getString("action"),
                        rs.getBoolean("dry_run"),
                        rs.getInt("affected_count"),
                        rs.getString("operator"),
                        rs.getString("detail"),
                        rs.getTimestamp("created_at").toInstant()
                ));
        StringBuilder csv = new StringBuilder("\uFEFFid,objectType,term,action,dryRun,affectedCount,operator,detail,createdAt\n");
        for (ArchiveRecordRow row : rows) {
            csv.append(row.id()).append(',')
                    .append(row.objectType()).append(',')
                    .append(row.term() == null ? "" : row.term()).append(',')
                    .append(row.action()).append(',')
                    .append(row.dryRun()).append(',')
                    .append(row.affectedCount()).append(',')
                    .append(row.operator()).append(',')
                    .append('"').append((row.detail() == null ? "" : row.detail()).replace("\"", "\"\"")).append('"').append(',')
                    .append(row.createdAt()).append('\n');
        }
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"data-archive-records.csv\"")
                .contentType(MediaType.parseMediaType("text/csv;charset=UTF-8"))
                .body(csv.toString());
    }

    private ArchiveRecordRow insertArchiveRecord(Principal principal, String objectType, String term, String action,
                                                 boolean dryRun, int count, String detail) {
        Instant now = Instant.now();
        jdbcTemplate.update("""
                        insert into data_archive_record
                          (object_type, term, action, dry_run, affected_count, operator, detail, created_at)
                        values (?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                objectType, blankToNull(term), action, dryRun, count, username(principal), detail, now);
        Long id = jdbcTemplate.queryForObject("select max(id) from data_archive_record where operator = ?", Long.class, username(principal));
        return new ArchiveRecordRow(id, objectType, blankToNull(term), action, dryRun, count, username(principal), detail, now);
    }

    private void insertBatchTask(Principal principal, String taskType, int success, int failure, String detail) {
        Instant now = Instant.now();
        jdbcTemplate.update("""
                        insert into batch_task
                          (task_type, operator, status, success_count, failure_count, failure_detail, started_at, ended_at)
                        values (?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                taskType, username(principal), failure > 0 ? "PARTIAL_SUCCESS" : "SUCCESS", success, failure, detail, now, now);
    }

    private int countObject(String objectType, String term) {
        Map<String, String> sql = new LinkedHashMap<>();
        sql.put("COURSE_SELECTION", "select count(*) from course_selection cs join course_offering co on co.id = cs.offering_id where (? is null or co.term = ?)");
        sql.put("GRADE", "select count(*) from academic_grade where (? is null or term = ?)");
        sql.put("EXAM", "select count(*) from exam_schedule e join course_offering co on co.id = e.course_offering_id where (? is null or co.term = ?)");
        sql.put("NOTICE", "select count(*) from notice where (? is null or concat('', created_at) like ?)");
        sql.put("AI_CALL_LOG", "select count(*) from ai_call_log where (? is null or concat('', created_at) like ?)");
        sql.put("AUDIT_LOG", "select count(*) from operation_audit_log where (? is null or concat('', created_at) like ?)");
        String normalized = objectType == null ? "" : objectType.trim().toUpperCase();
        if (!sql.containsKey(normalized)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "不支持的归档对象: " + objectType);
        }
        String arg = normalized.equals("NOTICE") || normalized.equals("AI_CALL_LOG") || normalized.equals("AUDIT_LOG")
                ? "%" + (term == null ? "" : term) + "%"
                : blankToNull(term);
        Long count = jdbcTemplate.queryForObject(sql.get(normalized), Long.class, blankToNull(term), arg);
        return count == null ? 0 : count.intValue();
    }

    private boolean isCurrentTerm(String term) {
        return term != null && (term.contains("2026") || term.contains("当前"));
    }

    private String username(Principal principal) {
        return principal == null ? "anonymous" : principal.getName();
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    public record BatchTaskRow(Long id, String taskType, String operator, Instant startedAt, Instant endedAt,
                               String status, Integer successCount, Integer failureCount, String failureDetail,
                               String reportPath) {
    }

    public record ArchivePreview(String objectType, String term, Integer affectedCount, boolean dryRun, String message) {
    }

    public record ArchiveRecordRow(Long id, String objectType, String term, String action, Boolean dryRun,
                                   Integer affectedCount, String operator, String detail, Instant createdAt) {
    }
}
