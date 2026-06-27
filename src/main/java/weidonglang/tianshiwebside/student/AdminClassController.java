package weidonglang.tianshiwebside.student;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import weidonglang.tianshiwebside.audit.AuditLogService;
import weidonglang.tianshiwebside.common.api.ApiResponse;
import weidonglang.tianshiwebside.common.cache.QueryCacheService;
import weidonglang.tianshiwebside.common.error.BusinessException;
import weidonglang.tianshiwebside.common.error.ErrorCode;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/api/admin/classes")
@PreAuthorize("hasRole('ADMIN')")
public class AdminClassController {
    private static final String UNASSIGNED_CLASS = "未分班";

    private final JdbcTemplate jdbcTemplate;
    private final PasswordEncoder passwordEncoder;
    private final AuditLogService auditLogService;
    private final QueryCacheService queryCacheService;

    public AdminClassController(
            JdbcTemplate jdbcTemplate,
            PasswordEncoder passwordEncoder,
            AuditLogService auditLogService,
            QueryCacheService queryCacheService
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.passwordEncoder = passwordEncoder;
        this.auditLogService = auditLogService;
        this.queryCacheService = queryCacheService;
    }

    @GetMapping
    public ApiResponse<List<ClassRow>> classes(@RequestParam(required = false) String keyword) {
        String normalizedKeyword = keyword == null || keyword.isBlank() ? null : "%" + keyword.trim() + "%";
        return ApiResponse.success(jdbcTemplate.query("""
                        select
                          ac.id,
                          ac.college,
                          ac.major,
                          ac.grade,
                          ac.class_name,
                          ac.advisor,
                          ht.username as homeroom_teacher_username,
                          ht.display_name as homeroom_teacher_name,
                          count(s.id) as student_count
                        from academic_class ac
                        left join sys_user ht on ht.id = ac.homeroom_teacher_user_id
                        left join student s on s.class_name = ac.class_name
                        where (? is null
                          or ac.class_name like ?
                          or ac.college like ?
                          or ac.major like ?)
                        group by ac.id, ac.college, ac.major, ac.grade, ac.class_name, ac.advisor, ht.username, ht.display_name
                        order by ac.grade desc, ac.college asc, ac.major asc, ac.class_name asc
                        """,
                (rs, rowNum) -> new ClassRow(
                        rs.getLong("id"),
                        rs.getString("college"),
                        rs.getString("major"),
                        rs.getString("grade"),
                        rs.getString("class_name"),
                        rs.getString("advisor"),
                        rs.getString("homeroom_teacher_username"),
                        rs.getString("homeroom_teacher_name"),
                        rs.getLong("student_count")
                ),
                normalizedKeyword,
                normalizedKeyword,
                normalizedKeyword,
                normalizedKeyword
        ));
    }

    @PostMapping
    @Transactional
    @PreAuthorize("hasAuthority('USER_WRITE')")
    public ApiResponse<ClassRow> createClass(Authentication authentication, @Valid @RequestBody ClassRequest request) {
        String className = request.className().trim();
        if (count("select count(*) from academic_class where class_name = ?", className) > 0) {
            throw new BusinessException(ErrorCode.CONFLICT, "班级名称已存在");
        }
        Long homeroomTeacherUserId = resolveTeacherUserId(request.homeroomTeacherUsername());
        String advisor = normalize(request.advisor(), teacherDisplayName(homeroomTeacherUserId));
        jdbcTemplate.update("""
                        insert into academic_class
                          (college, major, grade, class_name, advisor, homeroom_teacher_user_id, created_at, updated_at)
                        values (?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                request.college().trim(), request.major().trim(), request.grade().trim(), className,
                advisor, homeroomTeacherUserId, Instant.now(), Instant.now());
        Long classId = jdbcTemplate.queryForObject("select id from academic_class where class_name = ?", Long.class, className);
        auditLogService.record(authentication.getName(), "CREATE_CLASS", "CLASS", classId, className, null);
        evictClassFlowCaches();
        return ApiResponse.success(requireClass(classId));
    }

    @PutMapping("/{classId}")
    @Transactional
    @PreAuthorize("hasAuthority('USER_WRITE')")
    public ApiResponse<ClassRow> updateClass(Authentication authentication, @PathVariable Long classId, @Valid @RequestBody ClassRequest request) {
        ClassRow current = requireClass(classId);
        String nextClassName = request.className().trim();
        if (count("select count(*) from academic_class where class_name = ? and id <> ?", nextClassName, classId) > 0) {
            throw new BusinessException(ErrorCode.CONFLICT, "班级名称已存在");
        }
        Long homeroomTeacherUserId = resolveTeacherUserId(request.homeroomTeacherUsername());
        String advisor = normalize(request.advisor(), teacherDisplayName(homeroomTeacherUserId));
        jdbcTemplate.update("""
                        update academic_class
                        set college = ?,
                            major = ?,
                            grade = ?,
                            class_name = ?,
                            advisor = ?,
                            homeroom_teacher_user_id = ?,
                            updated_at = ?
                        where id = ?
                        """,
                request.college().trim(), request.major().trim(), request.grade().trim(), nextClassName,
                advisor, homeroomTeacherUserId, Instant.now(), classId);
        jdbcTemplate.update("""
                        update student
                        set college = ?, major = ?, grade = ?, class_name = ?
                        where class_name = ?
                        """,
                request.college().trim(), request.major().trim(), request.grade().trim(), nextClassName, current.className());
        auditLogService.record(authentication.getName(), "UPDATE_CLASS", "CLASS", classId, current.className() + " -> " + nextClassName, null);
        evictClassFlowCaches();
        return ApiResponse.success(requireClass(classId));
    }

    @DeleteMapping("/{classId}")
    @Transactional
    @PreAuthorize("hasAuthority('USER_WRITE')")
    public ApiResponse<Void> deleteClass(Authentication authentication, @PathVariable Long classId) {
        ClassRow current = requireClass(classId);
        if (current.studentCount() > 0) {
            throw new BusinessException(ErrorCode.CONFLICT, "班级内仍有学生，不能删除");
        }
        jdbcTemplate.update("delete from academic_class where id = ?", classId);
        auditLogService.record(authentication.getName(), "DELETE_CLASS", "CLASS", classId, current.className(), null);
        evictClassFlowCaches();
        return ApiResponse.success();
    }

    @GetMapping("/{classId}/students")
    public ApiResponse<List<ClassStudentRow>> students(@PathVariable Long classId) {
        ClassRow current = requireClass(classId);
        return ApiResponse.success(studentsByClassName(current.className()));
    }

    @PostMapping("/{classId}/students")
    @Transactional
    @PreAuthorize("hasAuthority('USER_WRITE')")
    public ApiResponse<ClassStudentRow> addStudent(Authentication authentication, @PathVariable Long classId, @Valid @RequestBody AddStudentRequest request) {
        ClassRow targetClass = requireClass(classId);
        StudentBrief student = requireStudentByNo(request.studentNo().trim());
        if (!isUnassigned(student.className()) && !student.className().equals(targetClass.className())) {
            throw new BusinessException(ErrorCode.CONFLICT, "学生已在其他班级，请使用转班操作");
        }
        assignStudentToClass(student.studentId(), targetClass);
        auditLogService.record(authentication.getName(), "ADD_STUDENT_TO_CLASS", "CLASS", classId, student.studentNo(), null);
        evictClassFlowCaches();
        return ApiResponse.success(requireClassStudent(student.studentId()));
    }

    @PostMapping("/{classId}/students/batch")
    @Transactional
    @PreAuthorize("hasAuthority('USER_WRITE')")
    public ApiResponse<BatchClassStudentResult> batchStudents(
            Authentication authentication,
            @PathVariable Long classId,
            @Valid @RequestBody BatchStudentsRequest request
    ) {
        ClassRow targetClass = requireClass(classId);
        List<String> errors = new ArrayList<>();
        int added = 0;
        int imported = 0;

        if (request.studentNos() != null) {
            for (String studentNo : request.studentNos()) {
                if (studentNo == null || studentNo.isBlank()) {
                    continue;
                }
                try {
                    StudentBrief student = requireStudentByNo(studentNo.trim());
                    if (!isUnassigned(student.className()) && !student.className().equals(targetClass.className())) {
                        errors.add(studentNo + " 已在其他班级");
                        continue;
                    }
                    assignStudentToClass(student.studentId(), targetClass);
                    added++;
                } catch (BusinessException ex) {
                    errors.add(studentNo + " " + ex.getMessage());
                }
            }
        }

        if (request.students() != null) {
            for (StudentImportRow row : request.students()) {
                try {
                    importStudent(row, targetClass);
                    imported++;
                } catch (BusinessException ex) {
                    errors.add(row.studentNo() + " " + ex.getMessage());
                }
            }
        }

        auditLogService.record(authentication.getName(), "BATCH_CLASS_STUDENTS", "CLASS", classId,
                "added=" + added + ", imported=" + imported + ", errors=" + errors.size(), null, errors.isEmpty(), String.join("; ", errors));
        evictClassFlowCaches();
        return ApiResponse.success(new BatchClassStudentResult(added, imported, errors.size(), errors));
    }

    @DeleteMapping("/{classId}/students/{studentId}")
    @Transactional
    @PreAuthorize("hasAuthority('USER_WRITE')")
    public ApiResponse<Void> removeStudent(Authentication authentication, @PathVariable Long classId, @PathVariable Long studentId) {
        ClassRow current = requireClass(classId);
        ClassStudentRow student = requireClassStudent(studentId);
        if (!student.className().equals(current.className())) {
            throw new BusinessException(ErrorCode.CONFLICT, "学生不属于该班级");
        }
        jdbcTemplate.update("update student set class_name = ? where id = ?", UNASSIGNED_CLASS, studentId);
        auditLogService.record(authentication.getName(), "REMOVE_STUDENT_FROM_CLASS", "CLASS", classId, student.studentNo(), null);
        evictClassFlowCaches();
        return ApiResponse.success();
    }

    @PostMapping("/{classId}/students/transfer")
    @Transactional
    @PreAuthorize("hasAuthority('USER_WRITE')")
    public ApiResponse<ClassStudentRow> transferStudent(
            Authentication authentication,
            @PathVariable Long classId,
            @Valid @RequestBody TransferStudentRequest request
    ) {
        ClassRow sourceClass = requireClass(classId);
        ClassRow targetClass = requireClass(request.targetClassId());
        ClassStudentRow student = requireClassStudent(request.studentId());
        if (!student.className().equals(sourceClass.className())) {
            throw new BusinessException(ErrorCode.CONFLICT, "学生不属于当前班级，不能从该班转出");
        }
        assignStudentToClass(request.studentId(), targetClass);
        auditLogService.record(authentication.getName(), "TRANSFER_STUDENT_CLASS", "CLASS", request.targetClassId(),
                student.studentNo() + ": " + sourceClass.className() + " -> " + targetClass.className(), null);
        evictClassFlowCaches();
        return ApiResponse.success(requireClassStudent(request.studentId()));
    }

    @PostMapping("/{classId}/students/batch-transfer")
    @Transactional
    @PreAuthorize("hasAuthority('USER_WRITE')")
    public ApiResponse<BatchTransferResult> batchTransferStudents(
            Authentication authentication,
            @PathVariable Long classId,
            @Valid @RequestBody BatchTransferStudentRequest request
    ) {
        ClassRow sourceClass = requireClass(classId);
        ClassRow targetClass = requireClass(request.targetClassId());
        List<String> errors = new ArrayList<>();
        int transferred = 0;
        for (Long studentId : request.studentIds()) {
            try {
                ClassStudentRow student = requireClassStudent(studentId);
                if (!student.className().equals(sourceClass.className())) {
                    errors.add(student.studentNo() + " 不属于当前班级");
                    continue;
                }
                assignStudentToClass(studentId, targetClass);
                transferred++;
            } catch (BusinessException ex) {
                errors.add(studentId + " " + ex.getMessage());
            }
        }
        auditLogService.record(authentication.getName(), "BATCH_TRANSFER_STUDENTS", "CLASS", request.targetClassId(),
                sourceClass.className() + " -> " + targetClass.className() + ", transferred=" + transferred
                        + ", errors=" + errors.size(),
                null, errors.isEmpty(), String.join("; ", errors));
        evictClassFlowCaches();
        return ApiResponse.success(new BatchTransferResult(transferred, errors.size(), errors));
    }

    private void importStudent(StudentImportRow row, ClassRow targetClass) {
        String studentNo = row.studentNo().trim();
        if (count("select count(*) from student where student_no = ?", studentNo) > 0
                || count("select count(*) from sys_user where username = ?", studentNo) > 0) {
            throw new BusinessException(ErrorCode.CONFLICT, "学号或账号已存在");
        }
        String college = normalize(row.college(), targetClass.college());
        String major = normalize(row.major(), targetClass.major());
        String grade = normalize(row.grade(), targetClass.grade());
        if (!college.equals(targetClass.college()) || !major.equals(targetClass.major()) || !grade.equals(targetClass.grade())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "导入学生的学院、专业或年级与班级不一致");
        }
        jdbcTemplate.update("""
                        insert into sys_user (username, password_hash, display_name, status)
                        values (?, ?, ?, 'ACTIVE')
                        """, studentNo, passwordEncoder.encode(normalize(row.initialPassword(), "123456")), row.name().trim());
        Long userId = jdbcTemplate.queryForObject("select id from sys_user where username = ?", Long.class, studentNo);
        jdbcTemplate.update("""
                        insert into sys_user_role (user_id, role_id)
                        select ?, id from sys_role where code = 'STUDENT'
                        """, userId);
        jdbcTemplate.update("""
                        insert into student (user_id, student_no, college, major, class_name, grade, status, phone, email, address)
                        values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                userId, studentNo, targetClass.college(), targetClass.major(), targetClass.className(), targetClass.grade(),
                "在籍", normalize(row.phone()), normalize(row.email()), null);
    }

    private void assignStudentToClass(Long studentId, ClassRow targetClass) {
        jdbcTemplate.update("""
                        update student
                        set college = ?, major = ?, grade = ?, class_name = ?
                        where id = ?
                        """,
                targetClass.college(), targetClass.major(), targetClass.grade(), targetClass.className(), studentId);
    }

    private ClassRow requireClass(Long classId) {
        List<ClassRow> rows = jdbcTemplate.query("""
                        select
                          ac.id,
                          ac.college,
                          ac.major,
                          ac.grade,
                          ac.class_name,
                          ac.advisor,
                          ht.username as homeroom_teacher_username,
                          ht.display_name as homeroom_teacher_name,
                          (select count(*) from student s where s.class_name = ac.class_name) as student_count
                        from academic_class ac
                        left join sys_user ht on ht.id = ac.homeroom_teacher_user_id
                        where ac.id = ?
                        """,
                (rs, rowNum) -> new ClassRow(
                        rs.getLong("id"),
                        rs.getString("college"),
                        rs.getString("major"),
                        rs.getString("grade"),
                        rs.getString("class_name"),
                        rs.getString("advisor"),
                        rs.getString("homeroom_teacher_username"),
                        rs.getString("homeroom_teacher_name"),
                        rs.getLong("student_count")
                ),
                classId);
        if (rows.isEmpty()) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "班级不存在");
        }
        return rows.get(0);
    }

    private StudentBrief requireStudentByNo(String studentNo) {
        List<StudentBrief> rows = jdbcTemplate.query("""
                        select id, student_no, class_name
                        from student
                        where student_no = ?
                        """,
                (rs, rowNum) -> new StudentBrief(rs.getLong("id"), rs.getString("student_no"), rs.getString("class_name")),
                studentNo);
        if (rows.isEmpty()) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "学生不存在");
        }
        return rows.get(0);
    }

    private ClassStudentRow requireClassStudent(Long studentId) {
        List<ClassStudentRow> rows = jdbcTemplate.query("""
                        select
                          s.id as student_id,
                          s.student_no,
                          u.display_name as name,
                          s.college,
                          s.major,
                          s.class_name,
                          s.grade,
                          s.status,
                          s.phone,
                          s.email
                        from student s
                        join sys_user u on u.id = s.user_id
                        where s.id = ?
                        """,
                (rs, rowNum) -> new ClassStudentRow(
                        rs.getLong("student_id"),
                        rs.getString("student_no"),
                        rs.getString("name"),
                        rs.getString("college"),
                        rs.getString("major"),
                        rs.getString("class_name"),
                        rs.getString("grade"),
                        rs.getString("status"),
                        rs.getString("phone"),
                        rs.getString("email")
                ),
                studentId);
        if (rows.isEmpty()) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "学生不存在");
        }
        return rows.get(0);
    }

    private List<ClassStudentRow> studentsByClassName(String className) {
        return jdbcTemplate.query("""
                        select
                          s.id as student_id,
                          s.student_no,
                          u.display_name as name,
                          s.college,
                          s.major,
                          s.class_name,
                          s.grade,
                          s.status,
                          s.phone,
                          s.email
                        from student s
                        join sys_user u on u.id = s.user_id
                        where s.class_name = ?
                        order by s.student_no asc
                        """,
                (rs, rowNum) -> new ClassStudentRow(
                        rs.getLong("student_id"),
                        rs.getString("student_no"),
                        rs.getString("name"),
                        rs.getString("college"),
                        rs.getString("major"),
                        rs.getString("class_name"),
                        rs.getString("grade"),
                        rs.getString("status"),
                        rs.getString("phone"),
                        rs.getString("email")
                ),
                className);
    }

    private long count(String sql, Object... args) {
        Long value = jdbcTemplate.queryForObject(sql, Long.class, args);
        return value == null ? 0 : value;
    }

    private boolean isUnassigned(String className) {
        return className == null || className.isBlank() || UNASSIGNED_CLASS.equals(className);
    }

    private String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String normalize(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private Long resolveTeacherUserId(String username) {
        if (username == null || username.isBlank()) {
            return null;
        }
        List<Long> rows = jdbcTemplate.query("""
                        select u.id
                        from sys_user u
                        join sys_user_role ur on ur.user_id = u.id
                        join sys_role r on r.id = ur.role_id
                        where u.username = ?
                          and r.code = 'TEACHER'
                        """,
                (rs, rowNum) -> rs.getLong("id"),
                username.trim());
        if (rows.isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "班主任账号不存在或不是教师角色");
        }
        return rows.get(0);
    }

    private String teacherDisplayName(Long userId) {
        if (userId == null) {
            return null;
        }
        return jdbcTemplate.queryForObject("select display_name from sys_user where id = ?", String.class, userId);
    }

    private void evictClassFlowCaches() {
        queryCacheService.evictByPrefix("query:student:");
        queryCacheService.evictByPrefix("query:dashboard:");
        queryCacheService.evictByPrefix("query:information:");
        queryCacheService.evictByPrefix("query:teacher:");
    }

    public record ClassRow(Long id, String college, String major, String grade, String className, String advisor,
                           String homeroomTeacherUsername, String homeroomTeacherName, Long studentCount) {
    }

    public record ClassStudentRow(Long studentId, String studentNo, String name, String college, String major,
                                  String className, String grade, String status, String phone, String email) {
    }

    private record StudentBrief(Long studentId, String studentNo, String className) {
    }

    public record ClassRequest(@NotBlank @Size(max = 80) String college,
                               @NotBlank @Size(max = 80) String major,
                               @NotBlank @Size(max = 20) String grade,
                               @NotBlank @Size(max = 80) String className,
                               @Size(max = 80) String advisor,
                               @Size(max = 64) String homeroomTeacherUsername) {
        public ClassRequest(String college, String major, String grade, String className, String advisor) {
            this(college, major, grade, className, advisor, null);
        }
    }

    public record AddStudentRequest(@NotBlank @Size(max = 32) String studentNo) {
    }

    public record BatchStudentsRequest(List<String> studentNos, List<@Valid StudentImportRow> students) {
    }

    public record StudentImportRow(@NotBlank @Size(max = 32) String studentNo,
                                   @NotBlank @Size(max = 64) String name,
                                   @Size(max = 80) String college,
                                   @Size(max = 80) String major,
                                   @Size(max = 20) String grade,
                                   @Pattern(regexp = "^$|^1[3-9]\\d{9}$", message = "手机号格式错误") String phone,
                                   @Email @Size(max = 120) String email,
                                   @Size(min = 6, max = 64) String initialPassword) {
    }

    public record TransferStudentRequest(@NotNull Long studentId, @NotNull Long targetClassId) {
    }

    public record BatchTransferStudentRequest(@NotNull Long targetClassId, @NotEmpty List<@NotNull Long> studentIds) {
    }

    public record BatchClassStudentResult(int addedCount, int importedCount, int errorCount, List<String> errors) {
    }

    public record BatchTransferResult(int transferredCount, int errorCount, List<String> errors) {
    }
}
