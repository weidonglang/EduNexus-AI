package weidonglang.tianshiwebside.student;

import com.fasterxml.jackson.core.type.TypeReference;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import weidonglang.tianshiwebside.audit.AuditLogService;
import weidonglang.tianshiwebside.common.api.ApiResponse;
import weidonglang.tianshiwebside.common.api.PageResponse;
import weidonglang.tianshiwebside.common.api.Pagination;
import weidonglang.tianshiwebside.common.cache.QueryCacheService;
import weidonglang.tianshiwebside.common.error.BusinessException;
import weidonglang.tianshiwebside.common.error.ErrorCode;
import weidonglang.tianshiwebside.common.trace.TraceIdHolder;
import weidonglang.tianshiwebside.governance.ContentModerationService;
import weidonglang.tianshiwebside.student.mapper.StudentMapper;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/api/students/me/status-changes")
public class StudentStatusChangeController {
    private final StudentMapper studentMapper;
    private final QueryCacheService queryCacheService;
    private final ContentModerationService moderationService;
    private final AuditLogService auditLogService;

    public StudentStatusChangeController(
            StudentMapper studentMapper,
            QueryCacheService queryCacheService,
            ContentModerationService moderationService,
            AuditLogService auditLogService
    ) {
        this.studentMapper = studentMapper;
        this.queryCacheService = queryCacheService;
        this.moderationService = moderationService;
        this.auditLogService = auditLogService;
    }

    @GetMapping
    public ApiResponse<PageResponse<StatusChangeApplicationResponse>> list(
            Authentication authentication,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        String username = authenticatedUsername(authentication);
        int safePage = Pagination.safePage(page);
        int safeSize = Pagination.safeSize(size);
        return ApiResponse.success(queryCacheService.get(
                "query:student:status-changes:" + username + ":" + safePage + ":" + safeSize,
                Duration.ofSeconds(20),
                new TypeReference<PageResponse<StatusChangeApplicationResponse>>() {
                },
                () -> new PageResponse<>(
                        studentMapper.findStatusChangesByUsername(username, safeSize, Pagination.offset(safePage, safeSize))
                                .stream()
                                .map(this::toResponse)
                                .toList(),
                        safePage,
                        safeSize,
                        studentMapper.countStatusChangesByUsername(username)
                )
        ));
    }

    @PostMapping
    public ApiResponse<StatusChangeApplicationResponse> submit(
            Authentication authentication,
            @Valid @RequestBody SubmitStatusChangeRequest request
    ) {
        String username = authenticatedUsername(authentication);
        moderationService.checkConfigured("STUDENT_CONTENT", request.reason(), username);
        Long studentId = studentMapper.findStudentIdByUsername(username);
        if (studentId == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "Student profile not found");
        }

        StudentMapper.InsertStatusChangeCommand command = new StudentMapper.InsertStatusChangeCommand(
                studentId,
                request.type(),
                request.reason().trim(),
                ApplicationStatus.SUBMITTED,
                Instant.now()
        );
        studentMapper.insertStatusChange(command);
        auditLogService.record(username, "SUBMIT_STATUS_CHANGE", "STATUS_CHANGE", command.getId(),
                request.type().name(), TraceIdHolder.get());
        queryCacheService.evictByPrefix("query:student:status-changes:" + authentication.getName());
        queryCacheService.evictByPrefix("query:admin:status-changes:");

        return ApiResponse.success(new StatusChangeApplicationResponse(
                command.getId(),
                command.getType(),
                command.getReason(),
                command.getStatus(),
                command.getSubmittedAt(),
                null,
                null
        ));
    }

    private String authenticatedUsername(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        return authentication.getName();
    }

    private StatusChangeApplicationResponse toResponse(StudentMapper.StatusChangeApplicationRow application) {
        return new StatusChangeApplicationResponse(
                application.id(),
                application.type(),
                application.reason(),
                application.status(),
                application.submittedAt(),
                application.reviewedAt(),
                application.reviewComment()
        );
    }

    public record SubmitStatusChangeRequest(
            @NotNull StatusChangeType type,
            @NotBlank @Size(max = 500) String reason
    ) {
    }

    public record StatusChangeApplicationResponse(
            Long id,
            StatusChangeType type,
            String reason,
            ApplicationStatus status,
            Instant submittedAt,
            Instant reviewedAt,
            String reviewComment
    ) {
    }
}
