package weidonglang.tianshiwebside;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import weidonglang.tianshiwebside.common.error.BusinessException;
import weidonglang.tianshiwebside.course.AdminCourseController;
import weidonglang.tianshiwebside.course.grab.CourseGrabCommand;
import weidonglang.tianshiwebside.course.grab.LocalCourseGrabService;
import weidonglang.tianshiwebside.course.mapper.AdminCourseOfferingRow;
import weidonglang.tianshiwebside.course.mapper.AdminCourseRow;
import weidonglang.tianshiwebside.student.AdminClassController;
import weidonglang.tianshiwebside.teacher.TeacherController;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class CourseFlowIntegrationTests {
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private AdminCourseController adminCourseController;

    @Autowired
    private TeacherController teacherController;

    @Autowired
    private LocalCourseGrabService courseGrabService;

    @Autowired
    private AdminClassController adminClassController;

    @BeforeEach
    void setUpSecurityContext() {
        SecurityContextHolder.getContext().setAuthentication(adminAuth());
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
        courseGrabService.setRedisEnabled(true);
    }

    @Test
    void adminCreatedOfferingIsVisibleToTeacher() {
        String suffix = uniqueSuffix();
        String teacherUsername = "teacher_" + suffix;
        String teacherName = "互通老师" + suffix;
        seedUser(teacherUsername, teacherName);

        AdminCourseRow course = adminCourseController.createCourse(new AdminCourseController.CourseCreateRequest(
                "FLOW" + suffix,
                "互通测试课程" + suffix,
                2,
                "专业选修"
        )).data();

        AdminCourseOfferingRow offering = adminCourseController.createOffering(adminAuth(), new AdminCourseController.CourseOfferingRequest(
                course.courseId(),
                teacherName,
                "2026-2027-1",
                30,
                "周一 1-2节",
                "测试楼 101",
                Instant.now().minusSeconds(60),
                Instant.now().plusSeconds(3600)
        )).data();

        var teacherOfferings = teacherController.offerings(auth(teacherUsername), "2026-2027-1").data();

        assertThat(teacherOfferings)
                .anyMatch(row -> row.offeringId().equals(offering.offeringId())
                        && row.teacherName().equals(teacherName)
                        && row.courseCode().equals(course.code()));
    }

    @Test
    void studentSelectionIsVisibleInTeacherGradeList() {
        String suffix = uniqueSuffix();
        String teacherUsername = "teacher_" + suffix;
        String teacherName = "选课可见老师" + suffix;
        String studentUsername = "student_" + suffix;
        seedUser(teacherUsername, teacherName);
        seedStudent(studentUsername, "选课学生" + suffix);

        AdminCourseRow course = adminCourseController.createCourse(new AdminCourseController.CourseCreateRequest(
                "SEL" + suffix,
                "选课互通课程" + suffix,
                3,
                "专业必修"
        )).data();
        AdminCourseOfferingRow offering = adminCourseController.createOffering(adminAuth(), new AdminCourseController.CourseOfferingRequest(
                course.courseId(),
                teacherName,
                "2026-2027-2",
                10,
                "周二 3-4节",
                "测试楼 202",
                Instant.now().minusSeconds(60),
                Instant.now().plusSeconds(3600)
        )).data();

        courseGrabService.setRedisEnabled(false);
        courseGrabService.grab(new CourseGrabCommand(studentUsername, offering.offeringId(), "req-" + suffix));

        var gradePage = teacherController.grades(auth(teacherUsername), "2026-2027-2", offering.offeringId(), studentUsername, 1, 20).data();

        assertThat(gradePage.records())
                .anyMatch(row -> row.offeringId().equals(offering.offeringId())
                        && row.studentNo().equals(studentUsername)
                        && row.courseCode().equals(course.code()));
    }

    @Test
    void databaseFallbackDoesNotOverfillOfferingCapacity() throws Exception {
        String suffix = uniqueSuffix();
        String teacherName = "容量老师" + suffix;
        seedUser("teacher_" + suffix, teacherName);
        Long courseId = seedCourse("CAP" + suffix, "容量保护课程" + suffix);
        Long offeringId = seedOffering(courseId, teacherName, "2026-2027-3", 3);

        List<String> usernames = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            String username = "cap_student_" + suffix + "_" + i;
            seedStudent(username, "容量学生" + i);
            usernames.add(username);
        }

        courseGrabService.setRedisEnabled(false);
        var executor = Executors.newFixedThreadPool(12);
        try {
            List<Callable<Boolean>> tasks = usernames.stream()
                    .<Callable<Boolean>>map(username -> () -> {
                        try {
                            courseGrabService.grab(new CourseGrabCommand(username, offeringId, "capacity-" + username));
                            return true;
                        } catch (BusinessException ignored) {
                            return false;
                        }
                    })
                    .toList();
            long successCount = executor.invokeAll(tasks).stream()
                    .filter(future -> {
                        try {
                            return future.get();
                        } catch (Exception ex) {
                            return false;
                        }
                    })
                    .count();

            Long selectedCount = jdbcTemplate.queryForObject(
                    "select count(*) from course_selection where offering_id = ?",
                    Long.class,
                    offeringId
            );

            assertThat(successCount).isEqualTo(3);
            assertThat(selectedCount).isEqualTo(3);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void adminCanImportAndTransferClassStudents() {
        String suffix = uniqueSuffix();
        var sourceClass = adminClassController.createClass(adminAuth(), new AdminClassController.ClassRequest(
                "信息工程学院",
                "软件工程",
                "2026",
                "软件工程闭环" + suffix + "A班",
                "张老师"
        )).data();
        var targetClass = adminClassController.createClass(adminAuth(), new AdminClassController.ClassRequest(
                "信息工程学院",
                "软件工程",
                "2026",
                "软件工程闭环" + suffix + "B班",
                "李老师"
        )).data();

        String studentNo = "class_student_" + suffix;
        var batchResult = adminClassController.batchStudents(adminAuth(), sourceClass.id(), new AdminClassController.BatchStudentsRequest(
                null,
                List.of(new AdminClassController.StudentImportRow(
                        studentNo,
                        "班级闭环学生" + suffix,
                        "信息工程学院",
                        "软件工程",
                        "2026",
                        "13812345678",
                        studentNo + "@example.com",
                        "123456"
                ))
        )).data();

        assertThat(batchResult.importedCount()).isEqualTo(1);
        var sourceStudents = adminClassController.students(sourceClass.id()).data();
        Long studentId = sourceStudents.stream()
                .filter(row -> row.studentNo().equals(studentNo))
                .findFirst()
                .orElseThrow()
                .studentId();

        adminClassController.transferStudent(adminAuth(), sourceClass.id(), new AdminClassController.TransferStudentRequest(
                studentId,
                targetClass.id()
        ));

        assertThat(adminClassController.students(sourceClass.id()).data()).noneMatch(row -> row.studentNo().equals(studentNo));
        assertThat(adminClassController.students(targetClass.id()).data()).anyMatch(row -> row.studentNo().equals(studentNo));
    }

    @Test
    void studentCannotSelectOfferingsWithSameSchedule() {
        String suffix = uniqueSuffix();
        String studentUsername = "conflict_student_" + suffix;
        seedStudent(studentUsername, "冲突学生" + suffix);
        Long firstCourseId = seedCourse("TC1" + suffix, "冲突课程一" + suffix);
        Long secondCourseId = seedCourse("TC2" + suffix, "冲突课程二" + suffix);
        Long firstOfferingId = seedOffering(firstCourseId, "冲突老师" + suffix, "2026-2027-4", 20);
        Long secondOfferingId = seedOffering(secondCourseId, "冲突老师" + suffix, "2026-2027-4", 20);

        courseGrabService.setRedisEnabled(false);
        courseGrabService.grab(new CourseGrabCommand(studentUsername, firstOfferingId, "first-" + suffix));

        assertThatThrownBy(() -> courseGrabService.grab(new CourseGrabCommand(studentUsername, secondOfferingId, "second-" + suffix)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("课程时间冲突");
    }

    private void seedUser(String username, String displayName) {
        jdbcTemplate.update("""
                insert into sys_user (username, password_hash, display_name, status)
                values (?, ?, ?, ?)
                """, username, "{noop}123456", displayName, "ACTIVE");
    }

    private void seedStudent(String username, String displayName) {
        seedUser(username, displayName);
        Long userId = jdbcTemplate.queryForObject("select id from sys_user where username = ?", Long.class, username);
        jdbcTemplate.update("""
                insert into student (user_id, student_no, college, major, class_name, grade, status, phone, email, address)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, userId, username, "信息工程学院", "软件工程", "软件工程 23-1", "2023", "在籍",
                "13800000000", username + "@example.com", "天津市");
    }

    private Long seedCourse(String code, String name) {
        jdbcTemplate.update("""
                insert into course (code, name, credit, category)
                values (?, ?, ?, ?)
                """, code, name, 2, "专业必修");
        return jdbcTemplate.queryForObject("select id from course where code = ?", Long.class, code);
    }

    private Long seedOffering(Long courseId, String teacherName, String term, int capacity) {
        jdbcTemplate.update("""
                insert into course_offering
                  (course_id, teacher_name, term, capacity, schedule_text, classroom, selection_start_at, selection_end_at)
                values (?, ?, ?, ?, ?, ?, ?, ?)
                """, courseId, teacherName, term, capacity, "周三 1-2节", "测试楼 303",
                Instant.now().minusSeconds(60), Instant.now().plusSeconds(3600));
        return jdbcTemplate.queryForObject("""
                select id
                from course_offering
                where course_id = ?
                  and teacher_name = ?
                  and term = ?
                """, Long.class, courseId, teacherName, term);
    }

    private Authentication auth(String username) {
        return new TestingAuthenticationToken(username, null, "ROLE_TEACHER");
    }

    private Authentication adminAuth() {
        return new TestingAuthenticationToken("admin_test", null, "ROLE_ADMIN", "COURSE_WRITE", "USER_WRITE");
    }

    private String uniqueSuffix() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    }
}
