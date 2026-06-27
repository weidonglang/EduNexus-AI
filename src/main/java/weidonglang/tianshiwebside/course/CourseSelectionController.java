package weidonglang.tianshiwebside.course;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import com.fasterxml.jackson.core.type.TypeReference;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import weidonglang.tianshiwebside.audit.AuditLogService;
import weidonglang.tianshiwebside.common.cache.QueryCacheService;
import weidonglang.tianshiwebside.common.api.ApiResponse;
import weidonglang.tianshiwebside.common.error.BusinessException;
import weidonglang.tianshiwebside.common.error.ErrorCode;
import weidonglang.tianshiwebside.common.trace.TraceIdHolder;
import weidonglang.tianshiwebside.course.grab.CourseGrabCommand;
import weidonglang.tianshiwebside.course.grab.CourseGrabPort;
import weidonglang.tianshiwebside.course.grab.CourseGrabResult;
import weidonglang.tianshiwebside.course.mapper.CourseOfferingRow;
import weidonglang.tianshiwebside.course.mapper.CourseSelectionReadMapper;
import weidonglang.tianshiwebside.course.mapper.CourseSelectionRow;
import weidonglang.tianshiwebside.course.mapper.CourseSelectionWriteMapper;

import java.time.Instant;
import java.time.Duration;
import java.util.List;

@RestController
@RequestMapping("/api/course-selection")
public class CourseSelectionController {
    private static final String CURRENT_TERM = "2025-2026-2";

    private final CourseGrabPort courseGrabPort;
    private final CourseSelectionReadMapper selectionReadMapper;
    private final CourseSelectionWriteMapper selectionWriteMapper;
    private final QueryCacheService queryCacheService;
    private final AuditLogService auditLogService;

    public CourseSelectionController(
            CourseGrabPort courseGrabPort,
            CourseSelectionReadMapper selectionReadMapper,
            CourseSelectionWriteMapper selectionWriteMapper,
            QueryCacheService queryCacheService,
            AuditLogService auditLogService
    ) {
        this.courseGrabPort = courseGrabPort;
        this.selectionReadMapper = selectionReadMapper;
        this.selectionWriteMapper = selectionWriteMapper;
        this.queryCacheService = queryCacheService;
        this.auditLogService = auditLogService;
    }

    @GetMapping("/offerings")
    /**
     * 功能：查询学生可选教学班列表。
     * 说明：前端选课页面进入时调用本接口，返回当前学期教学班、容量、已选人数、
     * 上课时间、教室和当前学生是否已选等信息，用于表格分页展示和抢课按钮状态判断。
     */
    public ApiResponse<PageResponse<CourseOfferingResponse>> offerings(
            Authentication authentication,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        String username = authenticatedUsername(authentication);
        int safePage = Math.max(page, 1);
        int safeSize = Math.max(10, Math.min(size, 100));
        int offset = (safePage - 1) * safeSize;
        String cacheKey = "query:course-selection:offerings:" + username + ":" + CURRENT_TERM + ":" + safePage + ":" + safeSize;
        PageResponse<CourseOfferingResponse> response = queryCacheService.get(
                cacheKey,
                Duration.ofSeconds(20),
                new TypeReference<PageResponse<CourseOfferingResponse>>() {
                },
                () -> {
                    List<CourseOfferingResponse> records = selectionReadMapper.findOfferings(username, CURRENT_TERM, safeSize, offset).stream()
                            .map(this::toOfferingResponse)
                            .toList();
                    return new PageResponse<>(records, safePage, safeSize, selectionReadMapper.countOfferings(CURRENT_TERM));
                }
        );
        return ApiResponse.success(response);
    }

    @GetMapping("/selected")
    /**
     * 功能：查询当前学生已经选择的课程。
     * 说明：根据登录账号关联学生档案和选课记录，返回已选教学班信息，
     * 供前端“已选课程”分页表格展示。
     */
    public ApiResponse<PageResponse<CourseSelectionResponse>> selected(
            Authentication authentication,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        String username = authenticatedUsername(authentication);
        int safePage = Math.max(page, 1);
        int safeSize = Math.max(5, Math.min(size, 100));
        int offset = (safePage - 1) * safeSize;
        String cacheKey = "query:course-selection:selected:" + username + ":" + safePage + ":" + safeSize;
        PageResponse<CourseSelectionResponse> response = queryCacheService.get(
                cacheKey,
                Duration.ofSeconds(20),
                new TypeReference<PageResponse<CourseSelectionResponse>>() {
                },
                () -> {
                    List<CourseSelectionResponse> records = selectionReadMapper.findSelectedCourses(username, safeSize, offset).stream()
                            .map(this::toSelectionResponse)
                            .toList();
                    return new PageResponse<>(records, safePage, safeSize, selectionReadMapper.countSelectedCourses(username));
                }
        );
        return ApiResponse.success(response);
    }

    @PostMapping("/offerings/{offeringId}")
    public ApiResponse<CourseSelectionResponse> select(Authentication authentication, @PathVariable Long offeringId) {
        CourseGrabResult result = grabCourse(authentication, offeringId).data();
        evictCourseFlowCaches(authentication.getName());
        return ApiResponse.success(toSelectionResponse(result));
    }

    @PostMapping("/grab/offerings/{offeringId}")
    /**
     * 功能：提供学生抢课接口。
     * 说明：接收前端传入的教学班 ID，调用抢课服务完成时间窗口校验、
     * Redis 库存扣减、重复选课校验和数据库选课记录写入。
     */
    public ApiResponse<CourseGrabResult> grab(Authentication authentication, @PathVariable Long offeringId) {
        ApiResponse<CourseGrabResult> response = grabCourse(authentication, offeringId);
        evictCourseFlowCaches(authentication.getName());
        return response;
    }

    @DeleteMapping("/selected/{selectionId}")
    /**
     * 功能：实现学生退课功能。
     * 说明：根据当前登录学生和选课记录 ID 删除 course_selection 数据，
     * 防止学生删除不属于自己的选课记录。
     */
    public ApiResponse<Void> drop(Authentication authentication, @PathVariable Long selectionId) {
        Long studentId = selectionWriteMapper.findStudentIdByUsername(authenticatedUsername(authentication));
        if (studentId == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "Student profile not found");
        }
        Long offeringId = selectionWriteMapper.findOfferingIdBySelection(selectionId, studentId);
        int deleted = selectionWriteMapper.deleteSelection(selectionId, studentId);
        if (deleted == 0) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "Course selection not found");
        }
        if (offeringId != null) {
            queryCacheService.evict("selection:offering:" + offeringId + ":remaining");
        }
        auditLogService.record(authentication.getName(), "DROP_COURSE", "COURSE_SELECTION", selectionId,
                "offeringId=" + offeringId, TraceIdHolder.get());
        evictCourseFlowCaches(authenticatedUsername(authentication));
        return ApiResponse.success();
    }

    private String authenticatedUsername(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        return authentication.getName();
    }

    private CourseOfferingResponse toOfferingResponse(CourseOfferingRow row) {
        SelectionWindowStatus windowStatus = windowStatus(row.selectionStartAt(), row.selectionEndAt(), Instant.now());
        return new CourseOfferingResponse(
                row.offeringId(),
                row.courseCode(),
                row.courseName(),
                row.credit(),
                row.category(),
                row.teacherName(),
                row.term(),
                row.capacity(),
                row.selectedCount(),
                row.scheduleText(),
                row.classroom(),
                row.selectionStartAt(),
                row.selectionEndAt(),
                windowStatus,
                windowStatus == SelectionWindowStatus.OPEN,
                Boolean.TRUE.equals(row.selected())
        );
    }

    private CourseSelectionResponse toSelectionResponse(CourseSelectionRow row) {
        return new CourseSelectionResponse(
                row.selectionId(),
                row.offeringId(),
                row.courseCode(),
                row.courseName(),
                row.credit(),
                row.teacherName(),
                row.scheduleText(),
                row.classroom(),
                row.selectedAt()
        );
    }

    private ApiResponse<CourseGrabResult> grabCourse(Authentication authentication, Long offeringId) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        CourseGrabResult result = courseGrabPort.grab(new CourseGrabCommand(authentication.getName(), offeringId, null));
        auditLogService.record(authentication.getName(), "SELECT_COURSE", "COURSE_SELECTION", result.selectionId(),
                "offeringId=" + result.offeringId(), TraceIdHolder.get());
        return ApiResponse.success(result);
    }

    @PostMapping("/grab")
    /**
     * 功能：支持带 requestId 的抢课请求。
     * 说明：前端每次点击抢课会生成唯一 requestId，后端用它配合 Redis 幂等 key
     * 防止重复点击或网络重试造成同一请求被重复处理。
     */
    public ApiResponse<CourseGrabResult> grabWithRequestId(
            Authentication authentication,
            @Valid @RequestBody CourseGrabRequest request
    ) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        CourseGrabResult result = courseGrabPort.grab(new CourseGrabCommand(
                authentication.getName(),
                request.offeringId(),
                request.requestId()
        ));
        auditLogService.record(authentication.getName(), "GRAB_COURSE", "COURSE_SELECTION", result.selectionId(),
                "offeringId=" + result.offeringId() + ", requestId=" + request.requestId(), TraceIdHolder.get());
        ApiResponse<CourseGrabResult> response = ApiResponse.success(result);
        evictCourseFlowCaches(authentication.getName());
        return response;
    }

    private void evictCourseFlowCaches(String username) {
        queryCacheService.evictByPrefix("query:course-selection:");
        queryCacheService.evictByPrefix("query:teacher:");
        queryCacheService.evictByPrefix("query:admin:course-offerings:");
        queryCacheService.evictByPrefix("query:schedule:");
        queryCacheService.evictByPrefix("query:information:");
        queryCacheService.evictByPrefix("query:dashboard:");
        queryCacheService.evictByPrefix("query:grades:" + username + ":");
        queryCacheService.evictByPrefix("query:exams:" + username + ":");
    }

    private SelectionWindowStatus windowStatus(Instant startAt, Instant endAt, Instant now) {
        if (now.isBefore(startAt)) {
            return SelectionWindowStatus.NOT_STARTED;
        }
        if (now.isAfter(endAt)) {
            return SelectionWindowStatus.ENDED;
        }
        return SelectionWindowStatus.OPEN;
    }

    private CourseSelectionResponse toSelectionResponse(CourseGrabResult result) {
        return new CourseSelectionResponse(
                result.selectionId(),
                result.offeringId(),
                result.courseCode(),
                result.courseName(),
                result.credit(),
                result.teacherName(),
                result.scheduleText(),
                result.classroom(),
                result.selectedAt()
        );
    }

    public record CourseOfferingResponse(
            Long offeringId,
            String courseCode,
            String courseName,
            Integer credit,
            String category,
            String teacherName,
            String term,
            Integer capacity,
            Long selectedCount,
            String scheduleText,
            String classroom,
            Instant selectionStartAt,
            Instant selectionEndAt,
            SelectionWindowStatus windowStatus,
            boolean selectableNow,
            boolean selected
    ) {
    }

    public record CourseSelectionResponse(
            Long selectionId,
            Long offeringId,
            String courseCode,
            String courseName,
            Integer credit,
            String teacherName,
            String scheduleText,
            String classroom,
            Instant selectedAt
    ) {
    }

    public record CourseGrabRequest(
            Long offeringId,
            @Size(max = 80) String requestId
    ) {
    }

    public record PageResponse<T>(List<T> records, int page, int size, long total) {
    }
}
