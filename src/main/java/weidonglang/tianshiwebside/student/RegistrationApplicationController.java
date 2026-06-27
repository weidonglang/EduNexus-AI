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
import weidonglang.tianshiwebside.student.mapper.RegistrationApplicationMapper;
import weidonglang.tianshiwebside.student.mapper.StudentMapper;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/api/students/me/registration-applications")
public class RegistrationApplicationController {
    private final StudentMapper studentMapper;
    private final RegistrationApplicationMapper applicationMapper;
    private final QueryCacheService queryCacheService;
    private final ContentModerationService moderationService;
    private final AuditLogService auditLogService;

    public RegistrationApplicationController(
            StudentMapper studentMapper,
            RegistrationApplicationMapper applicationMapper,
            QueryCacheService queryCacheService,
            ContentModerationService moderationService,
            AuditLogService auditLogService
    ) {
        this.studentMapper = studentMapper;
        this.applicationMapper = applicationMapper;
        this.queryCacheService = queryCacheService;
        this.moderationService = moderationService;
        this.auditLogService = auditLogService;
    }

    @GetMapping
    public ApiResponse<PageResponse<RegistrationApplicationMapper.RegistrationApplicationRow>> list(
            Authentication authentication,
            @RequestParam(required = false) RegistrationApplicationType type,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        String username = authenticatedUsername(authentication);
        int safePage = Pagination.safePage(page);
        int safeSize = Pagination.safeSize(size);
        return ApiResponse.success(queryCacheService.get(
                "query:student:registration-applications:" + username + ":" + (type == null ? "all" : type.name())
                        + ":" + safePage + ":" + safeSize,
                Duration.ofSeconds(20),
                new TypeReference<PageResponse<RegistrationApplicationMapper.RegistrationApplicationRow>>() {
                },
                () -> new PageResponse<>(
                        applicationMapper.findMine(username, type, safeSize, Pagination.offset(safePage, safeSize)),
                        safePage,
                        safeSize,
                        applicationMapper.countMine(username, type)
                )
        ));
    }

    @PostMapping
    public ApiResponse<RegistrationApplicationMapper.RegistrationApplicationRow> submit(
            Authentication authentication,
            @Valid @RequestBody SubmitRegistrationApplicationRequest request
    ) {
        String username = authenticatedUsername(authentication);
        moderationService.checkConfigured("STUDENT_CONTENT",
                request.targetName() + "\n" + normalizeOptional(request.courseName()) + "\n" + request.reason(), username);
        Long studentId = studentMapper.findStudentIdByUsername(username);
        if (studentId == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "Student profile not found");
        }

        RegistrationApplicationMapper.InsertRegistrationApplicationCommand command =
                new RegistrationApplicationMapper.InsertRegistrationApplicationCommand(
                        studentId,
                        request.type(),
                        request.targetName().trim(),
                        normalizeOptional(request.courseName()),
                        request.reason().trim(),
                        ApplicationStatus.SUBMITTED,
                        Instant.now()
        );
        applicationMapper.insert(command);
        auditLogService.record(username, "SUBMIT_REGISTRATION_APPLICATION", "REGISTRATION_APPLICATION", command.getId(),
                request.type().name(), TraceIdHolder.get());
        queryCacheService.evictByPrefix("query:student:registration-applications:" + authentication.getName());
        queryCacheService.evictByPrefix("query:admin:registration-applications:");

        return ApiResponse.success(new RegistrationApplicationMapper.RegistrationApplicationRow(
                command.getId(),
                command.getType(),
                command.getTargetName(),
                command.getCourseName(),
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

    private String normalizeOptional(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    public record SubmitRegistrationApplicationRequest(
            @NotNull RegistrationApplicationType type,
            @NotBlank @Size(max = 120) String targetName,
            @Size(max = 120) String courseName,
            @NotBlank @Size(max = 500) String reason
    ) {
    }
}
