package weidonglang.tianshiwebside;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class BatchUserImportClosureTests extends HttpRegressionTestSupport {
    @Test
    void userImportPreviewCommitWritesUserStudentTaskAndAudit() throws Exception {
        String suffix = suffix();
        String admin = "batch_user_admin_" + suffix;
        String student = "batch_user_student_" + suffix;
        String className = "批量用户班级-" + suffix;
        seedUser(admin, "批量用户管理员", List.of("ADMIN"), List.of("USER_WRITE", "USER_READ"));
        jdbcTemplate.update("""
                        insert into academic_class (college, major, grade, class_name, advisor, created_at, updated_at)
                        values (?, ?, ?, ?, ?, ?, ?)
                        """,
                "信息工程学院", "软件工程", "2026", className, "导入班主任", Instant.now(), Instant.now());

        String csv = """
                username,displayName,roleCodes,password,studentNo,college,major,grade,className,phone,email
                %s,批量导入学生,STUDENT,123456,%s,信息工程学院,软件工程,2026,%s,13800001111,%s@example.com
                """.formatted(student, student, className, student);
        String token = login(admin);

        JsonNode preview = json(post("/api/admin/users/import-preview", token, jsonContent(csv)), HttpStatus.OK);
        assertThat(preview.at("/data/validRows").asInt()).isEqualTo(1);
        assertThat(count("select count(*) from sys_user where username = ?", student)).isZero();

        JsonNode commit = json(post("/api/admin/users/import-commit", token, jsonContent(csv)), HttpStatus.OK);
        assertThat(commit.at("/data/successCount").asInt()).isEqualTo(1);
        assertThat(commit.at("/data/taskId").isNumber()).isTrue();
        assertThat(count("select count(*) from sys_user where username = ?", student)).isEqualTo(1);
        assertThat(count("select count(*) from student where student_no = ? and class_name = ?", student, className)).isEqualTo(1);
        assertThat(count("select count(*) from batch_task where task_type = 'USER_IMPORT' and operator = ?", admin)).isGreaterThanOrEqualTo(1);
        assertThat(count("select count(*) from operation_audit_log where action = 'BATCH_USER_IMPORT_COMMIT' and operator = ?", admin)).isGreaterThanOrEqualTo(1);
        String studentToken = login(student);
        assertThat(studentToken).isNotBlank();

        JsonNode myClass = json(get("/api/students/me/class", studentToken), HttpStatus.OK);
        assertThat(myClass.at("/data/className").asText()).isEqualTo(className);
        Long classId = jdbcTemplate.queryForObject("select id from academic_class where class_name = ?", Long.class, className);
        JsonNode classStudents = json(get("/api/admin/classes/" + classId + "/students", token), HttpStatus.OK);
        assertThat(classStudents.at("/data").toString()).contains(student, className);
    }

    @Test
    void userImportRejectsBadRowsAndNonAdminAccess() throws Exception {
        String suffix = suffix();
        String admin = "batch_user_bad_admin_" + suffix;
        String student = "batch_user_forbidden_" + suffix;
        seedUser(admin, "批量用户管理员", List.of("ADMIN"), List.of("USER_WRITE"));
        seedStudent(student, "无权学生");

        String badCsv = """
                username,displayName,roleCodes,password,studentNo,college,major,grade,className,phone,email
                bad-user,坏数据学生,STUDENT,123456,bad-user,信息工程学院,软件工程,2026,不存在班级,not-phone,bad-email
                """;
        JsonNode preview = json(post("/api/admin/users/import-preview", login(admin), jsonContent(badCsv)), HttpStatus.OK);
        assertThat(preview.at("/data/errorRows").asInt()).isEqualTo(1);
        assertThat(preview.at("/data/errors").toString()).contains("班级不存在", "邮箱格式错误");
        assertThat(post("/api/admin/users/import-preview", login(student), jsonContent(badCsv)).statusCode())
                .isEqualTo(HttpStatus.FORBIDDEN.value());
    }

    private String jsonContent(String content) throws Exception {
        return objectMapper.writeValueAsString(Map.of("content", content));
    }

    private int count(String sql, Object... args) {
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, args);
        return count == null ? 0 : count;
    }
}
