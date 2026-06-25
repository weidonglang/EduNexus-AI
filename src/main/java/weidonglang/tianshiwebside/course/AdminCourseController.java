package weidonglang.tianshiwebside.course;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import com.fasterxml.jackson.core.type.TypeReference;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import weidonglang.tianshiwebside.audit.AuditLogService;
import weidonglang.tianshiwebside.common.api.ApiResponse;
import weidonglang.tianshiwebside.common.cache.QueryCacheService;
import weidonglang.tianshiwebside.common.error.BusinessException;
import weidonglang.tianshiwebside.common.error.ErrorCode;
import weidonglang.tianshiwebside.course.mapper.AdminCourseMapper;
import weidonglang.tianshiwebside.course.mapper.AdminCourseOfferingRow;
import weidonglang.tianshiwebside.course.mapper.AdminCourseRow;

import java.time.Instant;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.nio.charset.StandardCharsets;
import java.util.List;

@RestController
@RequestMapping("/api/admin")
public class AdminCourseController {
    private final AdminCourseMapper adminCourseMapper;
    private final StringRedisTemplate redisTemplate;
    private final AuditLogService auditLogService;
    private final QueryCacheService queryCacheService;

    public AdminCourseController(
            AdminCourseMapper adminCourseMapper,
            StringRedisTemplate redisTemplate,
            AuditLogService auditLogService,
            QueryCacheService queryCacheService
    ) {
        this.adminCourseMapper = adminCourseMapper;
        this.redisTemplate = redisTemplate;
        this.auditLogService = auditLogService;
        this.queryCacheService = queryCacheService;
    }

    @GetMapping("/courses")
    /**
     * 功能：查询课程基础信息。
     * 说明：管理端课程教学班页面加载课程下拉框和课程表格时使用，
     * 课程是教学班的基础数据，一个课程可以在不同学期拆成多个教学班。
     */
    public ApiResponse<List<AdminCourseRow>> courses() {
        return ApiResponse.success(queryCacheService.get(
                "query:admin:courses:all",
                Duration.ofSeconds(30),
                new TypeReference<List<AdminCourseRow>>() {
                },
                adminCourseMapper::findCourses
        ));
    }

    @GetMapping(value = "/courses/export-csv", produces = "text/csv;charset=UTF-8")
    public String exportCoursesCsv() {
        StringBuilder builder = new StringBuilder("code,name,credit,category\n");
        for (AdminCourseRow course : adminCourseMapper.findCourses()) {
            builder.append(course.code()).append(',')
                    .append(course.name()).append(',')
                    .append(course.credit()).append(',')
                    .append(course.category()).append('\n');
        }
        return builder.toString();
    }

    @PostMapping("/courses/import-csv")
    @PreAuthorize("hasAuthority('COURSE_WRITE')")
    public ApiResponse<Void> importCoursesCsv(Authentication authentication, @RequestParam("file") MultipartFile file) throws java.io.IOException {
        String content = new String(file.getBytes(), StandardCharsets.UTF_8);
        for (String line : content.split("\\R")) {
            if (line.isBlank() || line.startsWith("code,")) {
                continue;
            }
            String[] parts = line.split(",", -1);
            if (parts.length < 4 || adminCourseMapper.countCourseByCode(parts[0].trim()) > 0) {
                continue;
            }
            AdminCourseMapper.CourseInsertCommand command = new AdminCourseMapper.CourseInsertCommand(
                    parts[0].trim(), parts[1].trim(), Integer.parseInt(parts[2].trim()), parts[3].trim());
            adminCourseMapper.insertCourse(command);
            auditLogService.record(authentication.getName(), "IMPORT_COURSE", "COURSE", command.getId(), parts[0].trim(), null);
        }
        evictCourseCaches();
        return ApiResponse.success();
    }

    @PostMapping("/courses")
    @PreAuthorize("hasAuthority('COURSE_WRITE')")
    /**
     * 功能：新增课程基础信息。
     * 说明：管理员维护课程编号、课程名称、学分和类别，后续创建教学班时会引用课程数据。
     */
    public ApiResponse<AdminCourseRow> createCourse(@Valid @RequestBody CourseCreateRequest request) {
        if (adminCourseMapper.countCourseByCode(request.code()) > 0) {
            throw new BusinessException(ErrorCode.CONFLICT, "课程编号已存在");
        }
        AdminCourseMapper.CourseInsertCommand command = new AdminCourseMapper.CourseInsertCommand(
                request.code().trim(),
                request.name().trim(),
                request.credit(),
                request.category().trim()
        );
        adminCourseMapper.insertCourse(command);
        evictCourseCaches();
        return ApiResponse.success(adminCourseMapper.findCourseById(command.getId()));
    }

    @PutMapping("/courses/{courseId}")
    @PreAuthorize("hasAuthority('COURSE_WRITE')")
    /**
     * 功能：修改课程基础信息。
     * 说明：更新课程编号、名称、学分和类别，同时校验课程编号不能和其他课程重复。
     */
    public ApiResponse<AdminCourseRow> updateCourse(
            Authentication authentication,
            @PathVariable Long courseId,
            @Valid @RequestBody CourseCreateRequest request
    ) {
        if (adminCourseMapper.findCourseById(courseId) == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "课程不存在");
        }
        String code = request.code().trim();
        if (adminCourseMapper.countCourseByCodeExceptId(code, courseId) > 0) {
            throw new BusinessException(ErrorCode.CONFLICT, "课程编号已存在");
        }
        AdminCourseMapper.CourseInsertCommand command = new AdminCourseMapper.CourseInsertCommand(
                code,
                request.name().trim(),
                request.credit(),
                request.category().trim()
        );
        command.setId(courseId);
        adminCourseMapper.updateCourse(command);
        evictCourseCaches();
        auditLogService.record(authentication.getName(), "UPDATE_COURSE", "COURSE", courseId, code, null);
        return ApiResponse.success(adminCourseMapper.findCourseById(courseId));
    }

    @DeleteMapping("/courses/{courseId}")
    @PreAuthorize("hasAuthority('COURSE_WRITE')")
    /**
     * 功能：删除课程基础信息。
     * 说明：如果课程已经开设教学班，则禁止直接删除，避免破坏教学班、选课和成绩数据关联。
     */
    public ApiResponse<Void> deleteCourse(Authentication authentication, @PathVariable Long courseId) {
        AdminCourseRow course = adminCourseMapper.findCourseById(courseId);
        if (course == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "课程不存在");
        }
        if (adminCourseMapper.countOfferingsByCourseId(courseId) > 0) {
            throw new BusinessException(ErrorCode.CONFLICT, "该课程已有教学班，不能直接删除");
        }
        adminCourseMapper.deleteCourse(courseId);
        evictCourseCaches();
        auditLogService.record(authentication.getName(), "DELETE_COURSE", "COURSE", courseId, course.code(), null);
        return ApiResponse.success();
    }

    @GetMapping("/course-offerings")
    /**
     * 功能：查询教学班列表。
     * 说明：教学班表示某门课程在某学期的具体开课实例，包含教师、容量、上课时间和教室；
     * 抢课使用的是教学班 ID，而不是课程 ID。
     */
    public ApiResponse<List<AdminCourseOfferingRow>> offerings(@RequestParam(required = false) String term) {
        String normalizedTerm = term == null || term.isBlank() ? null : term.trim();
        return ApiResponse.success(queryCacheService.get(
                "query:admin:course-offerings:" + (normalizedTerm == null ? "all" : normalizedTerm),
                Duration.ofSeconds(20),
                new TypeReference<List<AdminCourseOfferingRow>>() {
                },
                () -> adminCourseMapper.findOfferings(normalizedTerm)
        ));
    }

    @GetMapping("/course-offerings/options")
    public ApiResponse<CourseOfferingOptionsResponse> offeringOptions() {
        return ApiResponse.success(new CourseOfferingOptionsResponse(
                adminCourseMapper.findCourses(),
                adminCourseMapper.findTeacherOptions(),
                adminCourseMapper.findClassroomOptions(),
                adminCourseMapper.findTermOptions()
        ));
    }

    @GetMapping("/course-offerings/{offeringId}")
    public ApiResponse<AdminCourseOfferingRow> offering(@PathVariable Long offeringId) {
        AdminCourseOfferingRow row = adminCourseMapper.findOfferingById(offeringId);
        if (row == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "教学班不存在");
        }
        return ApiResponse.success(row);
    }

    @GetMapping("/course-offerings/{offeringId}/students")
    public ApiResponse<List<AdminCourseMapper.OfferingStudentRow>> offeringStudents(@PathVariable Long offeringId) {
        if (adminCourseMapper.findOfferingById(offeringId) == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "教学班不存在");
        }
        return ApiResponse.success(adminCourseMapper.findOfferingStudents(offeringId));
    }

    @PostMapping("/course-offerings/{offeringId}/publish")
    @PreAuthorize("hasAuthority('COURSE_WRITE')")
    public ApiResponse<AdminCourseOfferingRow> publishOffering(Authentication authentication, @PathVariable Long offeringId) {
        AdminCourseOfferingRow current = requireOffering(offeringId);
        Instant now = Instant.now();
        Instant endAt = current.selectionEndAt().isAfter(now) ? current.selectionEndAt() : now.plus(7, ChronoUnit.DAYS);
        adminCourseMapper.updateOfferingSelectionWindow(offeringId, now.minus(1, ChronoUnit.MINUTES), endAt);
        evictOfferingStock(offeringId);
        evictCourseCaches();
        auditLogService.record(authentication.getName(), "PUBLISH_COURSE_OFFERING", "COURSE_OFFERING", offeringId, current.courseName(), null);
        return ApiResponse.success(adminCourseMapper.findOfferingById(offeringId));
    }

    @PostMapping("/course-offerings/{offeringId}/close")
    @PreAuthorize("hasAuthority('COURSE_WRITE')")
    public ApiResponse<AdminCourseOfferingRow> closeOffering(Authentication authentication, @PathVariable Long offeringId) {
        AdminCourseOfferingRow current = requireOffering(offeringId);
        Instant now = Instant.now();
        adminCourseMapper.updateOfferingSelectionWindow(offeringId, current.selectionStartAt(), now.minus(1, ChronoUnit.MINUTES));
        evictOfferingStock(offeringId);
        evictCourseCaches();
        auditLogService.record(authentication.getName(), "CLOSE_COURSE_OFFERING", "COURSE_OFFERING", offeringId, current.courseName(), null);
        return ApiResponse.success(adminCourseMapper.findOfferingById(offeringId));
    }

    @PostMapping("/course-offerings")
    @PreAuthorize("hasAuthority('COURSE_WRITE')")
    /**
     * 功能：新增教学班。
     * 说明：管理员为课程设置任课教师、容量、上课安排和选课时间窗口，
     * 学生端可选课程列表就是基于这些教学班生成的。
     */
    public ApiResponse<AdminCourseOfferingRow> createOffering(Authentication authentication, @Valid @RequestBody CourseOfferingRequest request) {
        validateOfferingRequest(request, 0, null);
        AdminCourseMapper.CourseOfferingCommand command = toCommand(null, request);
        adminCourseMapper.insertOffering(command);
        evictCourseCaches();
        auditLogService.record(authentication.getName(), "CREATE_COURSE_OFFERING", "COURSE_OFFERING", command.getId(), request.term(), null);
        return ApiResponse.success(adminCourseMapper.findOfferingById(command.getId()));
    }

    @PutMapping("/course-offerings/{offeringId}")
    @PreAuthorize("hasAuthority('COURSE_WRITE')")
    /**
     * 功能：修改教学班信息。
     * 说明：更新容量、教师、时间或教室时会校验容量不能小于已选人数，
     * 并清理 Redis 库存缓存，保证下次抢课按最新数据库数据计算。
     */
    public ApiResponse<AdminCourseOfferingRow> updateOffering(
            Authentication authentication,
            @PathVariable Long offeringId,
            @Valid @RequestBody CourseOfferingRequest request
    ) {
        AdminCourseOfferingRow current = adminCourseMapper.findOfferingById(offeringId);
        if (current == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "教学班不存在");
        }
        long selectedCount = adminCourseMapper.countSelections(offeringId);
        validateOfferingRequest(request, selectedCount, offeringId);
        int updated = adminCourseMapper.updateOffering(toCommand(offeringId, request));
        if (updated == 0) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "教学班不存在");
        }
        evictOfferingStock(offeringId);
        evictCourseCaches();
        auditLogService.record(authentication.getName(), "UPDATE_COURSE_OFFERING", "COURSE_OFFERING", offeringId,
                "capacity=" + request.capacity() + ", classroom=" + request.classroom(), null);
        return ApiResponse.success(adminCourseMapper.findOfferingById(offeringId));
    }

    @DeleteMapping("/course-offerings/{offeringId}")
    @PreAuthorize("hasAuthority('COURSE_WRITE')")
    /**
     * 功能：删除教学班。
     * 说明：已有学生选课或被其他业务引用的教学班不能删除，防止影响课表、成绩和考试安排。
     */
    public ApiResponse<Void> deleteOffering(Authentication authentication, @PathVariable Long offeringId) {
        AdminCourseOfferingRow current = adminCourseMapper.findOfferingById(offeringId);
        if (current == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "教学班不存在");
        }
        if (adminCourseMapper.countSelections(offeringId) > 0) {
            throw new BusinessException(ErrorCode.CONFLICT, "已有学生选课，不能删除该教学班");
        }
        try {
            adminCourseMapper.deleteOffering(offeringId);
        } catch (DataIntegrityViolationException ex) {
            throw new BusinessException(ErrorCode.CONFLICT, "教学班已被其他业务引用，不能删除");
        }
        evictOfferingStock(offeringId);
        evictCourseCaches();
        auditLogService.record(authentication.getName(), "DELETE_COURSE_OFFERING", "COURSE_OFFERING", offeringId, current.courseName(), null);
        return ApiResponse.success();
    }

    private AdminCourseOfferingRow requireOffering(Long offeringId) {
        AdminCourseOfferingRow current = adminCourseMapper.findOfferingById(offeringId);
        if (current == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "教学班不存在");
        }
        return current;
    }

    private void validateOfferingRequest(CourseOfferingRequest request, long selectedCount, Long exceptOfferingId) {
        if (adminCourseMapper.findCourseById(request.courseId()) == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "课程不存在");
        }
        String teacherName = request.teacherName().trim();
        String term = request.term().trim();
        String scheduleText = request.scheduleText().trim();
        String classroom = request.classroom().trim();
        boolean teacherRoleInitialized = adminCourseMapper.countTeacherRoleDefinitions() > 0;
        boolean teacherAssignable = teacherRoleInitialized
                ? adminCourseMapper.countActiveTeacherByName(teacherName) > 0
                : adminCourseMapper.countActiveUserByDisplayName(teacherName) > 0;
        if (!teacherAssignable) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "授课教师不存在或未启用");
        }
        if (!request.selectionEndAt().isAfter(request.selectionStartAt())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "选课结束时间必须晚于开始时间");
        }
        if (request.capacity() < selectedCount) {
            throw new BusinessException(ErrorCode.CONFLICT, "容量不能小于已选人数");
        }
        if (adminCourseMapper.countTeacherScheduleConflicts(teacherName, term, scheduleText, exceptOfferingId) > 0) {
            throw new BusinessException(ErrorCode.CONFLICT, "同一教师同一时间不能被安排两门课");
        }
        if (adminCourseMapper.countClassroomScheduleConflicts(classroom, term, scheduleText, exceptOfferingId) > 0) {
            throw new BusinessException(ErrorCode.CONFLICT, "同一教室同一时间不能安排两门课");
        }
    }

    private AdminCourseMapper.CourseOfferingCommand toCommand(Long id, CourseOfferingRequest request) {
        return new AdminCourseMapper.CourseOfferingCommand(
                id,
                request.courseId(),
                request.teacherName().trim(),
                request.term().trim(),
                request.capacity(),
                request.scheduleText().trim(),
                request.classroom().trim(),
                request.selectionStartAt(),
                request.selectionEndAt()
        );
    }

    private void evictOfferingStock(Long offeringId) {
        try {
            // 教学班容量、时间或教室被管理员修改后，删除旧库存缓存。
            // 下一次抢课或预热时会重新按数据库数据生成 selection:offering:{offeringId}:remaining。
            redisTemplate.delete("selection:offering:" + offeringId + ":remaining");
        } catch (RuntimeException ignored) {
            // Redis is optional in local development; database state remains authoritative.
        }
    }

    private void evictCourseCaches() {
        queryCacheService.evictByPrefix("query:admin:courses:");
        queryCacheService.evictByPrefix("query:admin:course-offerings:");
        queryCacheService.evictByPrefix("query:course-selection:");
        queryCacheService.evictByPrefix("query:teacher:");
        queryCacheService.evictByPrefix("query:schedule:");
        queryCacheService.evictByPrefix("query:information:");
        queryCacheService.evictByPrefix("query:dashboard:");
    }

    public record CourseCreateRequest(
            @NotBlank String code,
            @NotBlank String name,
            @NotNull @Min(1) Integer credit,
            @NotBlank String category
    ) {
    }

    public record CourseOfferingRequest(
            @NotNull Long courseId,
            @NotBlank String teacherName,
            @NotBlank String term,
            @NotNull @Min(1) Integer capacity,
            @NotBlank String scheduleText,
            @NotBlank String classroom,
            @NotNull Instant selectionStartAt,
            @NotNull Instant selectionEndAt
    ) {
    }

    public record CourseOfferingOptionsResponse(
            List<AdminCourseRow> courses,
            List<String> teachers,
            List<String> classrooms,
            List<String> terms
    ) {
    }
}
