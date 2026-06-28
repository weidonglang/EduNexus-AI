package weidonglang.tianshiwebside;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class DashboardRoleScopeRegressionTests extends HttpRegressionTestSupport {
    @Test
    void dashboardCardsAreScopedByRoleAndCurrentUser() throws Exception {
        String suffix = suffix();
        String term = currentTerm();
        String admin = "dash_admin_" + suffix;
        String teacherA = "dash_teacher_a_" + suffix;
        String teacherB = "dash_teacher_b_" + suffix;
        String studentA = "dash_student_a_" + suffix;
        String studentB = "dash_student_b_" + suffix;
        seedUser(admin, "Dashboard 管理员", List.of("ADMIN"), List.of());
        seedUser(teacherA, "Dashboard 教师A-" + suffix, List.of("TEACHER"), List.of());
        seedUser(teacherB, "Dashboard 教师B-" + suffix, List.of("TEACHER"), List.of());
        seedStudent(studentA, "Dashboard 学生A");
        seedStudent(studentB, "Dashboard 学生B");

        long offeringA1 = seedOffering("DA1" + suffix, "Dashboard A1", "Dashboard 教师A-" + suffix, term);
        long offeringA2 = seedOffering("DA2" + suffix, "Dashboard A2", "Dashboard 教师A-" + suffix, term);
        long offeringB = seedOffering("DB1" + suffix, "Dashboard B1", "Dashboard 教师B-" + suffix, term);
        select(studentA, offeringA1);
        select(studentA, offeringA2);
        select(studentB, offeringA1);
        seedExam(offeringA1);
        seedExam(offeringB);
        notifyUser(studentA, "Dashboard 未读通知 A");

        JsonNode studentADashboard = json(get("/api/dashboard/me", login(studentA)), HttpStatus.OK);
        JsonNode studentBDashboard = json(get("/api/dashboard/me", login(studentB)), HttpStatus.OK);
        assertThat(studentADashboard.at("/data/roleView").asText()).isEqualTo("STUDENT");
        assertThat(studentADashboard.at("/data/cards").toString()).contains("本学期已选课程").doesNotContain("管理权限");
        assertThat(cardValue(studentADashboard, "selectedCourses")).isEqualTo(2);
        assertThat(cardValue(studentBDashboard, "selectedCourses")).isEqualTo(1);
        assertThat(cardValue(studentADashboard, "unreadNotices")).isEqualTo(1);
        assertThat(cardValue(studentBDashboard, "unreadNotices")).isZero();

        JsonNode teacherDashboard = json(get("/api/dashboard/me", login(teacherA)), HttpStatus.OK);
        assertThat(teacherDashboard.at("/data/roleView").asText()).isEqualTo("TEACHER");
        assertThat(teacherDashboard.at("/data/cards").toString()).contains("本学期教学班", "本人").doesNotContain("全局");
        assertThat(cardValue(teacherDashboard, "teacherOfferings")).isEqualTo(2);
        assertThat(cardValue(teacherDashboard, "teacherExams")).isEqualTo(1);

        JsonNode adminDashboard = json(get("/api/dashboard/me", login(admin)), HttpStatus.OK);
        assertThat(adminDashboard.at("/data/roleView").asText()).isEqualTo("ADMIN");
        assertThat(adminDashboard.at("/data/cards").toString()).contains("管理权限组", "全局", "系统级");

        String noRole = "dash_no_role_" + suffix;
        seedUser(noRole, "无角色用户", List.of(), List.of());
        assertThat(get("/api/dashboard/me", login(noRole)).statusCode()).isEqualTo(HttpStatus.FORBIDDEN.value());
    }

    private String currentTerm() {
        return jdbcTemplate.queryForObject("select config_value from system_config where config_key = 'current_term'", String.class);
    }

    private long seedOffering(String code, String name, String teacherName, String term) {
        jdbcTemplate.update("insert into course (code, name, credit, category) values (?, ?, ?, ?)",
                code, name, 2, "专业课");
        Long courseId = jdbcTemplate.queryForObject("select id from course where code = ?", Long.class, code);
        jdbcTemplate.update("""
                        insert into course_offering
                          (course_id, teacher_name, term, capacity, schedule_text, classroom, selection_start_at, selection_end_at)
                        values (?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                courseId, teacherName, term, 30, "周一 1-2节", "D101",
                Instant.parse("2025-01-01T00:00:00Z"),
                Instant.parse("2026-12-31T23:59:59Z"));
        return jdbcTemplate.queryForObject("select id from course_offering where course_id = ?", Long.class, courseId);
    }

    private void select(String studentNo, long offeringId) {
        Long studentId = jdbcTemplate.queryForObject("select id from student where student_no = ?", Long.class, studentNo);
        jdbcTemplate.update("insert into course_selection (student_id, offering_id, selected_at) values (?, ?, ?)",
                studentId, offeringId, Instant.now());
    }

    private void seedExam(long offeringId) {
        jdbcTemplate.update("""
                        insert into exam_schedule (course_offering_id, exam_time, room, seat_no, exam_type, status)
                        values (?, ?, ?, ?, ?, ?)
                        """,
                offeringId, LocalDateTime.parse("2026-06-30T09:00:00"), "D201", "1-40", "期末考试", "PUBLISHED");
    }

    private void notifyUser(String username, String title) {
        Long userId = jdbcTemplate.queryForObject("select id from sys_user where username = ?", Long.class, username);
        jdbcTemplate.update("""
                        insert into user_notification
                          (user_id, title, content, category, read_flag, created_at, related_type, related_id)
                        values (?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                userId, title, title, "GENERAL", false, Instant.now(), "NOTICE", 1L);
    }

    private int cardValue(JsonNode dashboard, String key) {
        for (JsonNode card : dashboard.at("/data/cards")) {
            if (key.equals(card.at("/key").asText())) {
                return card.at("/value").asInt();
            }
        }
        throw new AssertionError("Missing dashboard card: " + key);
    }
}
