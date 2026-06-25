package weidonglang.tianshiwebside;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import weidonglang.tianshiwebside.academic.AcademicAdminController;
import weidonglang.tianshiwebside.academic.AcademicQueryController;
import weidonglang.tianshiwebside.audit.AuditLogController;
import weidonglang.tianshiwebside.common.api.PageResponse;
import weidonglang.tianshiwebside.course.grab.CourseGrabCommand;
import weidonglang.tianshiwebside.course.grab.LocalCourseGrabService;
import weidonglang.tianshiwebside.evaluation.AdminTeachingEvaluationController;
import weidonglang.tianshiwebside.evaluation.TeachingEvaluationController;
import weidonglang.tianshiwebside.file.AdminFileController;
import weidonglang.tianshiwebside.file.StatusChangeAttachmentController;
import weidonglang.tianshiwebside.file.StatusChangeAttachmentMapper;
import weidonglang.tianshiwebside.notice.AdminNoticeController;
import weidonglang.tianshiwebside.notice.NoticeController;
import weidonglang.tianshiwebside.notice.mapper.NoticeMapper;
import weidonglang.tianshiwebside.permission.MenuController;
import weidonglang.tianshiwebside.permission.RolePermissionController;
import weidonglang.tianshiwebside.student.AdminRegistrationApplicationController;
import weidonglang.tianshiwebside.student.AdminStatusChangeController;
import weidonglang.tianshiwebside.student.ApplicationStatus;
import weidonglang.tianshiwebside.student.RegistrationApplicationController;
import weidonglang.tianshiwebside.student.RegistrationApplicationType;
import weidonglang.tianshiwebside.student.StatusChangeType;
import weidonglang.tianshiwebside.student.StudentProfileController;
import weidonglang.tianshiwebside.student.StudentStatusChangeController;
import weidonglang.tianshiwebside.teacher.TeacherController;
import weidonglang.tianshiwebside.user.AdminUserController;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class SystemInteroperabilityIntegrationTests {
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private AdminUserController adminUserController;
    @Autowired
    private RolePermissionController rolePermissionController;
    @Autowired
    private MenuController menuController;
    @Autowired
    private StudentStatusChangeController studentStatusChangeController;
    @Autowired
    private AdminStatusChangeController adminStatusChangeController;
    @Autowired
    private StudentProfileController studentProfileController;
    @Autowired
    private RegistrationApplicationController registrationApplicationController;
    @Autowired
    private AdminRegistrationApplicationController adminRegistrationApplicationController;
    @Autowired
    private NoticeController noticeController;
    @Autowired
    private AdminNoticeController adminNoticeController;
    @Autowired
    private AcademicAdminController academicAdminController;
    @Autowired
    private AcademicQueryController academicQueryController;
    @Autowired
    private TeachingEvaluationController teachingEvaluationController;
    @Autowired
    private AdminTeachingEvaluationController adminTeachingEvaluationController;
    @Autowired
    private TeacherController teacherController;
    @Autowired
    private StatusChangeAttachmentController attachmentController;
    @Autowired
    private AdminFileController adminFileController;
    @Autowired
    private AuditLogController auditLogController;
    @Autowired
    private LocalCourseGrabService courseGrabService;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.getContext().setAuthentication(adminAuth());
    }

    @AfterEach
    void tearDown() {
        courseGrabService.setRedisEnabled(true);
        SecurityContextHolder.clearContext();
    }

    @Test
    void adminUserRoleAndRoleMenuChangesAffectVisibleMenus() {
        ensureRole("STUDENT", "学生");
        ensureMenu("dashboard", "首页", "/dashboard", null, 1);
        String suffix = suffix();

        var createdUser = adminUserController.createUser(new AdminUserController.CreateUserRequest(
                "menu_student_" + suffix,
                "菜单学生" + suffix,
                "123456",
                List.of("STUDENT")
        )).data();
        Long studentRoleId = roleId("STUDENT");

        rolePermissionController.updateRoleMenus(adminAuth(), studentRoleId,
                new RolePermissionController.UpdateRoleMenusRequest(List.of("dashboard")));
        var menus = menuController.menus(auth(createdUser.username(), "ROLE_STUDENT")).data();

        assertThat(createdUser.roleCodes()).contains("STUDENT");
        assertThat(menus).anyMatch(menu -> menu.code().equals("dashboard") && menu.path().equals("/dashboard"));
    }

    @Test
    void statusChangeReviewUpdatesStudentProfileAndNotification() {
        String username = "status_student_" + suffix();
        seedStudent(username, "学籍学生");

        var application = studentStatusChangeController.submit(
                auth(username, "ROLE_STUDENT"),
                new StudentStatusChangeController.SubmitStatusChangeRequest(StatusChangeType.SUSPEND, "申请休学")
        ).data();
        assertThat(adminStatusChangeController.applications(ApplicationStatus.SUBMITTED, username, 1, 20).data().records())
                .anyMatch(row -> row.id().equals(application.id()));

        adminStatusChangeController.review(adminAuth(), application.id(),
                new AdminStatusChangeController.ReviewStatusChangeRequest(AdminStatusChangeController.ReviewDecision.APPROVE, "同意"));

        var profile = studentProfileController.myProfile(auth(username, "ROLE_STUDENT")).data();
        var notifications = noticeController.notifications(auth(username, "ROLE_STUDENT"), false, 1, 20).data();

        assertThat(profile.status()).isEqualTo("休学");
        assertThat(notifications.records()).anyMatch(row -> row.title().contains("学籍异动审核结果"));
    }

    @Test
    void registrationApplicationReviewIsVisibleToStudentAndNotification() {
        String username = "reg_student_" + suffix();
        seedStudent(username, "报名学生");

        var application = registrationApplicationController.submit(
                auth(username, "ROLE_STUDENT"),
                new RegistrationApplicationController.SubmitRegistrationApplicationRequest(
                        RegistrationApplicationType.RETAKE_REGISTRATION,
                        "重修目标",
                        "高等数学",
                        "需要重修"
                )
        ).data();
        assertThat(adminRegistrationApplicationController.applications(ApplicationStatus.SUBMITTED, RegistrationApplicationType.RETAKE_REGISTRATION, username, 1, 20).data().records())
                .anyMatch(row -> row.id().equals(application.id()));

        adminRegistrationApplicationController.review(adminAuth(), application.id(),
                new AdminRegistrationApplicationController.ReviewRegistrationApplicationRequest(
                        AdminRegistrationApplicationController.ReviewDecision.APPROVE,
                        "通过"
                ));

        var mine = registrationApplicationController.list(auth(username, "ROLE_STUDENT"), RegistrationApplicationType.RETAKE_REGISTRATION, 1, 20).data();
        var notifications = noticeController.notifications(auth(username, "ROLE_STUDENT"), false, 1, 20).data();

        assertThat(mine.records()).anyMatch(row -> row.id().equals(application.id()) && row.status() == ApplicationStatus.APPROVED);
        assertThat(notifications.records()).anyMatch(row -> row.title().contains("报名申请审核结果"));
    }

    @Test
    void adminGradeAndExamChangesAreVisibleToStudentQueries() {
        String suffix = suffix();
        String studentUsername = "academic_student_" + suffix;
        seedStudent(studentUsername, "成绩考试学生");
        Long courseId = seedCourse("ACD" + suffix, "成绩考试课程" + suffix);
        Long offeringId = seedOffering(courseId, "考试老师" + suffix, "2027-2028-1", 20);
        seedSelection(studentUsername, offeringId);

        academicAdminController.createGrade(adminAuth(), new AcademicAdminController.GradeRequest(
                studentUsername,
                courseId,
                "2027-2028-1",
                91,
                BigDecimal.valueOf(4.10),
                "正常考试",
                "PUBLISHED",
                false
        ));
        academicAdminController.createExam(adminAuth(), new AcademicAdminController.ExamRequest(
                offeringId,
                LocalDateTime.now().plusDays(7),
                "考场 A101",
                "01",
                "期末考试",
                "已发布",
                "监考老师"
        ));

        var grades = academicQueryController.grades(auth(studentUsername, "ROLE_STUDENT"), 1, 20).data();
        var exams = academicQueryController.exams(auth(studentUsername, "ROLE_STUDENT"), 1, 20).data();

        assertThat(grades.records()).anyMatch(row -> row.courseName().contains("成绩考试课程") && row.score().equals(91));
        assertThat(exams.records()).anyMatch(row -> row.courseName().contains("成绩考试课程") && row.room().equals("考场 A101"));
    }

    @Test
    void studentEvaluationIsVisibleToTeacherAndAdminSummaries() {
        String suffix = suffix();
        String studentUsername = "eval_student_" + suffix;
        String teacherUsername = "eval_teacher_" + suffix;
        String teacherName = "评价老师" + suffix;
        seedStudent(studentUsername, "评价学生");
        seedUser(teacherUsername, teacherName, List.of("TEACHER"));
        Long courseId = seedCourse("EVA" + suffix, "评价课程" + suffix);
        Long offeringId = seedOffering(courseId, teacherName, "2027-2028-2", 20);
        seedSelection(studentUsername, offeringId);

        var tasksBefore = teachingEvaluationController.tasks(auth(studentUsername, "ROLE_STUDENT")).data();
        teachingEvaluationController.submit(auth(studentUsername, "ROLE_STUDENT"), offeringId,
                new TeachingEvaluationController.SubmitEvaluationRequest(5, 5, 4, 5, "课程很好"));

        var adminSummaries = adminTeachingEvaluationController.summaries("2027-2028-2").data();
        var teacherSummaries = teacherController.evaluations(auth(teacherUsername, "ROLE_TEACHER"), "2027-2028-2").data();

        assertThat(tasksBefore).anyMatch(row -> row.offeringId().equals(offeringId) && !row.evaluated());
        assertThat(adminSummaries).anyMatch(row -> row.offeringId().equals(offeringId)
                && row.submittedCount().equals(1L)
                && row.averageOverallScore() >= 5.0);
        assertThat(teacherSummaries).anyMatch(row -> row.offeringId().equals(offeringId)
                && row.submittedCount().equals(1L)
                && row.averageOverallScore() >= 5.0);
        assertThat(adminTeachingEvaluationController.records("2027-2028-2", offeringId).data())
                .anyMatch(row -> row.studentNo().equals(studentUsername) && row.comment().equals("课程很好"));
    }

    @Test
    void noticePublishIsVisibleOnHomeAndUserNotificationCanBeMarkedRead() {
        ensureRole("STUDENT", "学生");
        String username = "notice_student_" + suffix();
        seedStudent(username, "通知学生");

        var notice = adminNoticeController.publish(adminAuth(), new AdminNoticeController.PublishNoticeRequest(
                "互通公告",
                "公告内容",
                "NOTICE",
                true,
                "STUDENT"
        )).data();

        PageResponse<NoticeMapper.NotificationRow> unread = noticeController.notifications(auth(username, "ROLE_STUDENT"), false, 1, 20).data();
        Long notificationId = unread.records().stream()
                .filter(row -> row.title().equals("互通公告"))
                .findFirst()
                .orElseThrow()
                .id();
        noticeController.markRead(auth(username, "ROLE_STUDENT"), notificationId);

        assertThat(noticeController.home("NOTICE", 1, 10).data().records()).anyMatch(row -> row.id().equals(notice.id()));
        assertThat(noticeController.notifications(auth(username, "ROLE_STUDENT"), true, 1, 20).data().records())
                .anyMatch(row -> row.id().equals(notificationId) && row.readFlag());
    }

    @Test
    void statusChangeAttachmentUploadIsVisibleToAdminFilesAndCanBeDeleted() {
        String username = "file_student_" + suffix();
        seedStudent(username, "文件学生");
        var application = studentStatusChangeController.submit(
                auth(username, "ROLE_STUDENT"),
                new StudentStatusChangeController.SubmitStatusChangeRequest(StatusChangeType.OTHER, "上传材料")
        ).data();

        MockMultipartFile file = new MockMultipartFile("file", "proof.txt", "text/plain", "证明材料".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        attachmentController.upload(auth(username, "ROLE_STUDENT"), application.id(), file);
        List<StatusChangeAttachmentMapper.AdminAttachmentRow> adminFiles = adminFileController.files().data();
        Long attachmentId = adminFiles.stream()
                .filter(row -> row.applicationId().equals(application.id()) && row.originalFilename().equals("proof.txt"))
                .findFirst()
                .orElseThrow()
                .id();

        adminFileController.delete(attachmentId);

        assertThat(attachmentController.list(application.id()).data()).noneMatch(row -> row.id().equals(attachmentId));
    }

    @Test
    void auditedAdminOperationIsVisibleInAuditLogs() {
        ensureRole("STUDENT", "学生");
        ensureMenu("dashboard", "首页", "/dashboard", null, 1);
        ensureMenu("audit_dash_" + suffix(), "审计测试菜单", "/audit-test", null, 99);
        Long studentRoleId = roleId("STUDENT");

        rolePermissionController.updateRoleMenus(adminAuth(), studentRoleId,
                new RolePermissionController.UpdateRoleMenusRequest(List.of("dashboard")));

        assertThat(auditLogController.logs("UPDATE_ROLE_MENUS", 1, 20).data().records())
                .anyMatch(row -> row.action().equals("UPDATE_ROLE_MENUS"));
    }

    private void seedSelection(String studentUsername, Long offeringId) {
        Long studentId = jdbcTemplate.queryForObject("""
                select s.id
                from student s
                join sys_user u on u.id = s.user_id
                where u.username = ?
                """, Long.class, studentUsername);
        jdbcTemplate.update("insert into course_selection (student_id, offering_id, selected_at) values (?, ?, ?)",
                studentId, offeringId, Instant.now());
    }

    private void seedStudent(String username, String displayName) {
        seedUser(username, displayName, List.of("STUDENT"));
        Long userId = jdbcTemplate.queryForObject("select id from sys_user where username = ?", Long.class, username);
        jdbcTemplate.update("""
                insert into student (user_id, student_no, college, major, class_name, grade, status, phone, email, address)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, userId, username, "信息工程学院", "软件工程", "软件工程 23-1", "2023", "在籍",
                "13800000000", username + "@example.com", "天津市");
    }

    private void seedUser(String username, String displayName, List<String> roleCodes) {
        jdbcTemplate.update("""
                insert into sys_user (username, password_hash, display_name, status)
                values (?, ?, ?, ?)
                """, username, "{noop}123456", displayName, "ACTIVE");
        Long userId = jdbcTemplate.queryForObject("select id from sys_user where username = ?", Long.class, username);
        for (String roleCode : roleCodes) {
            ensureRole(roleCode, roleCode);
            jdbcTemplate.update("""
                    insert into sys_user_role (user_id, role_id)
                    select ?, id
                    from sys_role
                    where code = ?
                    """, userId, roleCode);
        }
    }

    private Long seedCourse(String code, String name) {
        jdbcTemplate.update("insert into course (code, name, credit, category) values (?, ?, ?, ?)", code, name, 3, "专业必修");
        return jdbcTemplate.queryForObject("select id from course where code = ?", Long.class, code);
    }

    private Long seedOffering(Long courseId, String teacherName, String term, int capacity) {
        jdbcTemplate.update("""
                insert into course_offering
                  (course_id, teacher_name, term, capacity, schedule_text, classroom, selection_start_at, selection_end_at)
                values (?, ?, ?, ?, ?, ?, ?, ?)
                """, courseId, teacherName, term, capacity, "周一 1-2节", "测试楼 101",
                Instant.now().minusSeconds(60), Instant.now().plusSeconds(3600));
        return jdbcTemplate.queryForObject("""
                select id
                from course_offering
                where course_id = ?
                  and teacher_name = ?
                  and term = ?
                """, Long.class, courseId, teacherName, term);
    }

    private void ensureRole(String code, String name) {
        Integer count = jdbcTemplate.queryForObject("select count(*) from sys_role where code = ?", Integer.class, code);
        if (count == null || count == 0) {
            jdbcTemplate.update("insert into sys_role (code, name) values (?, ?)", code, name);
        }
    }

    private void ensureMenu(String code, String title, String path, String parentCode, int sortOrder) {
        Integer count = jdbcTemplate.queryForObject("select count(*) from sys_menu where code = ?", Integer.class, code);
        if (count == null || count == 0) {
            jdbcTemplate.update("""
                    insert into sys_menu (code, title, path, icon, parent_code, sort_order)
                    values (?, ?, ?, ?, ?, ?)
                    """, code, title, path, "LayoutDashboard", parentCode, sortOrder);
        }
    }

    private Long roleId(String code) {
        return jdbcTemplate.queryForObject("select id from sys_role where code = ?", Long.class, code);
    }

    private Authentication auth(String username, String... authorities) {
        return new TestingAuthenticationToken(username, null, authorities);
    }

    private Authentication adminAuth() {
        return new TestingAuthenticationToken("admin_test", null,
                "ROLE_ADMIN",
                "USER_WRITE",
                "ROLE_PERMISSION_WRITE",
                "STATUS_REVIEW",
                "NOTICE_WRITE",
                "GRADE_READ",
                "GRADE_WRITE",
                "EXAM_WRITE",
                "AUDIT_READ"
        );
    }

    private String suffix() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    }
}
