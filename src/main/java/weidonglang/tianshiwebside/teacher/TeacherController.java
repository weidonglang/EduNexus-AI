package weidonglang.tianshiwebside.teacher;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import com.fasterxml.jackson.core.type.TypeReference;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import weidonglang.tianshiwebside.audit.AuditLogService;
import weidonglang.tianshiwebside.common.cache.QueryCacheService;
import weidonglang.tianshiwebside.academic.mapper.AcademicAdminMapper;
import weidonglang.tianshiwebside.academic.mapper.AcademicAdminMapper.ExamAdminRow;
import weidonglang.tianshiwebside.common.api.ApiResponse;
import weidonglang.tianshiwebside.common.error.BusinessException;
import weidonglang.tianshiwebside.common.error.ErrorCode;
import weidonglang.tianshiwebside.common.trace.TraceIdHolder;
import weidonglang.tianshiwebside.evaluation.mapper.EvaluationSummaryRow;
import weidonglang.tianshiwebside.teacher.mapper.TeacherMapper;
import weidonglang.tianshiwebside.teacher.mapper.TeacherMapper.HomeroomClassRow;
import weidonglang.tianshiwebside.teacher.mapper.TeacherMapper.HomeroomClassStudentRow;
import weidonglang.tianshiwebside.teacher.mapper.TeacherMapper.TeacherGradeEntryRow;
import weidonglang.tianshiwebside.teacher.mapper.TeacherMapper.TeacherOfferingRow;
import weidonglang.tianshiwebside.teacher.mapper.TeacherMapper.TeacherOfferingStudentRow;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 教师端业务接口。
 *
 * 教师登录后可以查看本人任课教学班、录入或修改学生成绩、维护考试安排、查看评价结果。
 * 所有查询和写入都会按当前教师身份过滤，避免教师操作不属于自己的课程。
 */
@RestController
@RequestMapping("/api/teacher")
public class TeacherController {
    private final TeacherMapper teacherMapper;
    private final QueryCacheService queryCacheService;
    private final AuditLogService auditLogService;

    public TeacherController(TeacherMapper teacherMapper, QueryCacheService queryCacheService, AuditLogService auditLogService) {
        this.teacherMapper = teacherMapper;
        this.queryCacheService = queryCacheService;
        this.auditLogService = auditLogService;
    }

    /**
     * 查询当前教师本学期任课教学班。
     *
     * 教师端工作台和成绩录入页面会使用这个列表作为课程筛选条件。
     */
    @GetMapping("/offerings")
    public ApiResponse<List<TeacherOfferingRow>> offerings(Authentication authentication, @RequestParam(required = false) String term) {
        String teacherName = teacherName(authentication);
        String normalizedTerm = normalize(term);
        return ApiResponse.success(queryCacheService.get(
                "query:teacher:offerings:" + teacherName + ":" + (normalizedTerm == null ? "all" : normalizedTerm),
                Duration.ofSeconds(20),
                new TypeReference<List<TeacherOfferingRow>>() {
                },
                () -> teacherMapper.findOfferings(teacherName, normalizedTerm)
        ));
    }

    /**
     * 分页查询教师可录入成绩的学生名单。
     *
     * 分页是为了防止一个教学班或压测数据较多时页面卡顿；教师只能看到自己教学班下的学生。
     */
    @GetMapping("/grades")
    public ApiResponse<PageResponse<TeacherGradeEntryRow>> grades(
            Authentication authentication,
            @RequestParam(required = false) String term,
            @RequestParam(required = false) Long offeringId,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "50") int size
    ) {
        String teacherName = teacherName(authentication);
        int safePage = Math.max(page, 1);
        int safeSize = Math.max(10, Math.min(size, 200));
        int offset = (safePage - 1) * safeSize;
        String normalizedKeyword = keyword == null || keyword.isBlank() ? null : "%" + keyword.trim() + "%";
        String normalizedTerm = normalize(term);
        String cacheKey = "query:teacher:grades:" + teacherName + ":" + (normalizedTerm == null ? "all" : normalizedTerm)
                + ":" + (offeringId == null ? "all" : offeringId)
                + ":" + (normalizedKeyword == null ? "all" : normalizedKeyword)
                + ":" + safePage + ":" + safeSize;
        PageResponse<TeacherGradeEntryRow> response = queryCacheService.get(
                cacheKey,
                Duration.ofSeconds(15),
                new TypeReference<PageResponse<TeacherGradeEntryRow>>() {
                },
                () -> {
                    List<TeacherGradeEntryRow> records = teacherMapper.findGradeEntries(
                            teacherName,
                            normalizedTerm,
                            offeringId,
                            normalizedKeyword,
                            safeSize,
                            offset
                    );
                    long total = teacherMapper.countGradeEntries(teacherName, normalizedTerm, offeringId, normalizedKeyword);
                    return new PageResponse<>(records, safePage, safeSize, total);
                }
        );
        return ApiResponse.success(response);
    }

    @GetMapping("/course-offerings/{offeringId}/students")
    public ApiResponse<List<TeacherOfferingStudentRow>> offeringStudents(Authentication authentication, @PathVariable Long offeringId) {
        String teacherName = teacherName(authentication);
        ensureOwnedOffering(teacherName, offeringId);
        return ApiResponse.success(queryCacheService.get(
                "query:teacher:offering-students:" + teacherName + ":" + offeringId,
                Duration.ofSeconds(15),
                new TypeReference<List<TeacherOfferingStudentRow>>() {
                },
                () -> teacherMapper.findOfferingStudents(teacherName, offeringId)
        ));
    }

    @GetMapping("/classes")
    public ApiResponse<List<HomeroomClassRow>> homeroomClasses(Authentication authentication) {
        String username = authenticatedUsername(authentication);
        return ApiResponse.success(queryCacheService.get(
                "query:teacher:homeroom-classes:" + username,
                Duration.ofSeconds(20),
                new TypeReference<List<HomeroomClassRow>>() {
                },
                () -> teacherMapper.findHomeroomClassesByUsername(username)
        ));
    }

    @GetMapping("/classes/{classId}/students")
    public ApiResponse<List<HomeroomClassStudentRow>> homeroomClassStudents(Authentication authentication, @PathVariable Long classId) {
        String username = authenticatedUsername(authentication);
        if (teacherMapper.countOwnedHomeroomClass(username, classId) == 0) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "只能查看本人负责班级的学生名单");
        }
        return ApiResponse.success(queryCacheService.get(
                "query:teacher:homeroom-class-students:" + username + ":" + classId,
                Duration.ofSeconds(15),
                new TypeReference<List<HomeroomClassStudentRow>>() {
                },
                () -> teacherMapper.findHomeroomClassStudents(username, classId)
        ));
    }

    /**
     * 保存教师录入或修改的成绩。
     *
     * 后端会校验教学班归属、成绩是否锁定，并自动计算绩点，防止教师越权修改其他课程成绩。
     */
    @PostMapping("/grades")
    public ApiResponse<Void> saveGrade(Authentication authentication, @Valid @RequestBody TeacherGradeRequest request) {
        String teacherName = teacherName(authentication);
        Long studentId = teacherMapper.findOwnedSelectedStudentId(teacherName, request.offeringId(), request.studentNo().trim());
        Long courseId = teacherMapper.findOwnedOfferingCourseId(teacherName, request.offeringId());
        if (studentId == null || courseId == null) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "只能录入本人教学班中已选课学生的成绩");
        }
        Long gradeId = request.gradeId();
        if (gradeId == null) {
            gradeId = teacherMapper.findOwnedGradeId(teacherName, request.offeringId(), studentId, courseId, request.term().trim());
        } else if (teacherMapper.countOwnedGrade(teacherName, gradeId) == 0) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "只能修改本人任课课程的成绩");
        }
        boolean updatingExistingGrade = gradeId != null;
        if (updatingExistingGrade && (request.reason() == null || request.reason().isBlank())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "修改成绩必须填写原因");
        }
        if (gradeId != null && teacherMapper.countLockedGrade(gradeId) > 0) {
            throw new BusinessException(ErrorCode.CONFLICT, "成绩已锁定，不能修改");
        }
        Integer oldScore = updatingExistingGrade ? teacherMapper.findGradeScore(gradeId) : null;
        AcademicAdminMapper.GradeCommand command = new AcademicAdminMapper.GradeCommand(
                gradeId,
                studentId,
                courseId,
                request.term().trim(),
                request.score(),
                calculateGradePoint(request.score()),
                request.examType().trim(),
                request.gradeStatus().trim(),
                false
        );
        if (gradeId == null) {
            teacherMapper.insertGrade(command);
            auditLogService.record(authentication.getName(), "CREATE_GRADE", "GRADE", command.getId(),
                    request.studentNo().trim() + "=" + request.score(), TraceIdHolder.get());
        } else {
            teacherMapper.updateGrade(command);
            teacherMapper.insertGradeChangeLog(gradeId, oldScore, request.score(), request.reason().trim(),
                    authentication.getName(), "TEACHER", TraceIdHolder.get());
            auditLogService.record(authentication.getName(), "UPDATE_GRADE", "GRADE", gradeId,
                    request.studentNo().trim() + ": " + oldScore + " -> " + request.score(), TraceIdHolder.get());
        }
        evictTeacherAcademicCaches();
        return ApiResponse.success();
    }

    /**
     * 查询教师本人课程的考试安排。
     */
    @GetMapping("/exams")
    public ApiResponse<List<ExamAdminRow>> exams(Authentication authentication, @RequestParam(required = false) String term) {
        String teacherName = teacherName(authentication);
        String normalizedTerm = normalize(term);
        return ApiResponse.success(queryCacheService.get(
                "query:teacher:exams:" + teacherName + ":" + (normalizedTerm == null ? "all" : normalizedTerm),
                Duration.ofSeconds(20),
                new TypeReference<List<ExamAdminRow>>() {
                },
                () -> teacherMapper.findExams(teacherName, normalizedTerm)
        ));
    }

    @PostMapping("/exams")
    public ApiResponse<Void> createExam(Authentication authentication, @Valid @RequestBody TeacherExamRequest request) {
        String teacherName = teacherName(authentication);
        ensureOwnedOffering(teacherName, request.offeringId());
        AcademicAdminMapper.ExamCommand command = toExamCommand(null, request);
        teacherMapper.insertExam(command);
        auditLogService.record(authentication.getName(), "CREATE_EXAM", "EXAM", command.getId(),
                "offeringId=" + request.offeringId(), TraceIdHolder.get());
        evictTeacherAcademicCaches();
        return ApiResponse.success();
    }

    @PutMapping("/exams/{examId}")
    public ApiResponse<Void> updateExam(Authentication authentication, @PathVariable Long examId, @Valid @RequestBody TeacherExamRequest request) {
        String teacherName = teacherName(authentication);
        ensureOwnedExam(teacherName, examId);
        ensureOwnedOffering(teacherName, request.offeringId());
        teacherMapper.updateExam(toExamCommand(examId, request));
        auditLogService.record(authentication.getName(), "UPDATE_EXAM", "EXAM", examId,
                "offeringId=" + request.offeringId(), TraceIdHolder.get());
        evictTeacherAcademicCaches();
        return ApiResponse.success();
    }

    @DeleteMapping("/exams/{examId}")
    public ApiResponse<Void> deleteExam(Authentication authentication, @PathVariable Long examId) {
        String teacherName = teacherName(authentication);
        ensureOwnedExam(teacherName, examId);
        teacherMapper.deleteExam(examId);
        auditLogService.record(authentication.getName(), "DELETE_EXAM", "EXAM", examId, null, TraceIdHolder.get());
        evictTeacherAcademicCaches();
        return ApiResponse.success();
    }

    @GetMapping("/evaluations")
    public ApiResponse<List<EvaluationSummaryRow>> evaluations(Authentication authentication, @RequestParam(required = false) String term) {
        String teacherName = teacherName(authentication);
        String normalizedTerm = normalize(term);
        return ApiResponse.success(queryCacheService.get(
                "query:teacher:evaluations:" + teacherName + ":" + (normalizedTerm == null ? "all" : normalizedTerm),
                Duration.ofSeconds(20),
                new TypeReference<List<EvaluationSummaryRow>>() {
                },
                () -> teacherMapper.findEvaluationSummaries(teacherName, normalizedTerm)
        ));
    }

    private String teacherName(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        String displayName = teacherMapper.findDisplayNameByUsername(authentication.getName());
        if (displayName == null || displayName.isBlank()) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "教师账号不存在");
        }
        return displayName;
    }

    private String authenticatedUsername(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        return authentication.getName();
    }

    private String normalize(String term) {
        return term == null || term.isBlank() ? null : term.trim();
    }

    private void ensureOwnedOffering(String teacherName, Long offeringId) {
        if (teacherMapper.countOwnedOffering(teacherName, offeringId) == 0) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "只能维护本人任课教学班的考试安排");
        }
    }

    private void ensureOwnedExam(String teacherName, Long examId) {
        if (teacherMapper.countOwnedExam(teacherName, examId) == 0) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "只能维护本人任课教学班的考试安排");
        }
    }

    private void evictTeacherAcademicCaches() {
        queryCacheService.evictByPrefix("query:teacher:");
        queryCacheService.evictByPrefix("query:grades:");
        queryCacheService.evictByPrefix("query:exams:");
        queryCacheService.evictByPrefix("query:dashboard:");
    }

    private AcademicAdminMapper.ExamCommand toExamCommand(Long id, TeacherExamRequest request) {
        return new AcademicAdminMapper.ExamCommand(id, request.offeringId(), request.examTime(), request.room().trim(),
                request.seatNo().trim(), request.examType().trim(), request.status().trim(),
                request.invigilator() == null ? null : request.invigilator().trim());
    }

    public record TeacherExamRequest(@NotNull Long offeringId, @NotNull LocalDateTime examTime, @NotBlank String room,
                                     @NotBlank String seatNo, @NotBlank String examType, @NotBlank String status,
                                     String invigilator) {
    }

    public record TeacherGradeRequest(Long gradeId, @NotNull Long offeringId, @NotBlank String studentNo,
                                      @NotBlank String term, @NotNull @Min(0) @Max(100) Integer score,
                                      @NotBlank String examType, @NotBlank String gradeStatus, String reason) {
    }

    public record PageResponse<T>(List<T> records, int page, int size, long total) {
    }

    private BigDecimal calculateGradePoint(int score) {
        if (score < 60) {
            return BigDecimal.ZERO.setScale(2);
        }
        BigDecimal point = BigDecimal.valueOf(score - 50).divide(BigDecimal.TEN, 2, RoundingMode.HALF_UP);
        return point.min(BigDecimal.valueOf(5.00)).setScale(2, RoundingMode.HALF_UP);
    }
}
