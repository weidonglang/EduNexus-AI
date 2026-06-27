package weidonglang.tianshiwebside.evaluation;

import com.fasterxml.jackson.core.type.TypeReference;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import weidonglang.tianshiwebside.audit.AuditLogService;
import weidonglang.tianshiwebside.common.api.ApiResponse;
import weidonglang.tianshiwebside.common.cache.QueryCacheService;
import weidonglang.tianshiwebside.common.error.BusinessException;
import weidonglang.tianshiwebside.common.error.ErrorCode;
import weidonglang.tianshiwebside.common.trace.TraceIdHolder;
import weidonglang.tianshiwebside.evaluation.mapper.EvaluationTaskRow;
import weidonglang.tianshiwebside.evaluation.mapper.TeachingEvaluationMapper;
import weidonglang.tianshiwebside.governance.ContentModerationService;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * 学生教学评价接口。
 *
 * 学生只能评价自己已经选过的教学班，系统会校验选课记录和是否重复评价。
 * 这一模块体现了“学生选课数据”和“教学评价数据”之间的业务关联。
 */
@RestController
@RequestMapping("/api/evaluations")
public class TeachingEvaluationController {
    private final TeachingEvaluationMapper evaluationMapper;
    private final QueryCacheService queryCacheService;
    private final ContentModerationService moderationService;
    private final AuditLogService auditLogService;

    public TeachingEvaluationController(
            TeachingEvaluationMapper evaluationMapper,
            QueryCacheService queryCacheService,
            ContentModerationService moderationService,
            AuditLogService auditLogService
    ) {
        this.evaluationMapper = evaluationMapper;
        this.queryCacheService = queryCacheService;
        this.moderationService = moderationService;
        this.auditLogService = auditLogService;
    }

    /**
     * 查询当前学生待评价课程。
     *
     * 前端教学评价页面先展示任务列表，学生选择课程后再提交评价分数和文字意见。
     */
    @GetMapping("/tasks")
    public ApiResponse<List<EvaluationTaskRow>> tasks(Authentication authentication) {
        String username = authenticatedUsername(authentication);
        return ApiResponse.success(queryCacheService.get(
                "query:evaluations:tasks:" + username,
                Duration.ofSeconds(20),
                new TypeReference<List<EvaluationTaskRow>>() {
                },
                () -> evaluationMapper.findTasksByUsername(username)
        ));
    }

    /**
     * 提交某个教学班的评价。
     *
     * 后端会检查：学生是否存在、是否选过该课、是否已经评价过，避免伪造请求或重复提交。
     */
    @PostMapping("/tasks/{offeringId}")
    public ApiResponse<Void> submit(
            Authentication authentication,
            @PathVariable Long offeringId,
            @Valid @RequestBody SubmitEvaluationRequest request
    ) {
        String username = authenticatedUsername(authentication);
        if (request.comment() != null && !request.comment().isBlank()) {
            moderationService.checkConfigured("STUDENT_CONTENT", request.comment(), username);
        }
        Long studentId = evaluationMapper.findStudentIdByUsername(username);
        if (studentId == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "学生档案不存在");
        }
        if (evaluationMapper.countSelection(studentId, offeringId) == 0) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "只能评价已选课程");
        }
        if (evaluationMapper.countEvaluation(studentId, offeringId) > 0) {
            throw new BusinessException(ErrorCode.CONFLICT, "该课程已提交评价");
        }
        evaluationMapper.insertEvaluation(new TeachingEvaluationMapper.InsertEvaluationCommand(
                studentId,
                offeringId,
                request.teachingScore(),
                request.contentScore(),
                request.interactionScore(),
                request.overallScore(),
                request.comment() == null ? null : request.comment().trim(),
                Instant.now()
        ));
        auditLogService.record(username, "SUBMIT_TEACHING_EVALUATION", "TEACHING_EVALUATION", offeringId,
                "overall=" + request.overallScore(), TraceIdHolder.get());
        queryCacheService.evictByPrefix("query:evaluations:");
        queryCacheService.evictByPrefix("query:teacher:evaluations:");
        queryCacheService.evictByPrefix("query:dashboard:" + username);
        return ApiResponse.success();
    }

    private String authenticatedUsername(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        return authentication.getName();
    }

    public record SubmitEvaluationRequest(
            @NotNull @Min(1) @Max(5) Integer teachingScore,
            @NotNull @Min(1) @Max(5) Integer contentScore,
            @NotNull @Min(1) @Max(5) Integer interactionScore,
            @NotNull @Min(1) @Max(5) Integer overallScore,
            @Size(max = 500) String comment
    ) {
    }
}
