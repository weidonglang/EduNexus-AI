package weidonglang.tianshiwebside;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class BatchCourseOfferingImportClosureTests extends HttpRegressionTestSupport {
    @Test
    void courseAndOfferingImportAreVisibleAndCreateBatchTask() throws Exception {
        String suffix = suffix();
        String admin = "batch_course_admin_" + suffix;
        String teacher = "batch_course_teacher_" + suffix;
        String student = "batch_course_student_" + suffix;
        String code = "BC" + suffix.toUpperCase();
        String term = "2031-2032-1";
        seedUser(admin, "批量课程管理员", List.of("ADMIN"), List.of("COURSE_WRITE"));
        seedUser(teacher, "批量任课教师-" + suffix, List.of("TEACHER"), List.of());
        seedStudent(student, "批量选课学生");

        String token = login(admin);
        String courseCsv = """
                courseCode,courseName,credit,category,courseType,department,description
                %s,批量闭环课程,3,专业课,必修,信息工程学院,批量课程导入闭环
                """.formatted(code);
        JsonNode courseCommit = json(post("/api/admin/courses/import-commit", token, jsonContent(courseCsv)), HttpStatus.OK);
        assertThat(courseCommit.at("/data/successCount").asInt()).isEqualTo(1);
        assertThat(count("select count(*) from course where code = ?", code)).isEqualTo(1);

        String offeringCsv = """
                courseCode,term,teacherName,capacity,scheduleText,classroom,selectionStartAt,selectionEndAt,status
                %s,%s,批量任课教师-%s,35,周一 1-2节,A101,2031-08-20T00:00:00Z,2031-09-30T23:59:59Z,OPEN
                """.formatted(code, term, suffix);
        JsonNode offeringCommit = json(post("/api/admin/course-offerings/import-commit", token, jsonContent(offeringCsv)), HttpStatus.OK);
        assertThat(offeringCommit.at("/data/successCount").asInt()).isEqualTo(1);
        Long offeringId = jdbcTemplate.queryForObject("""
                select co.id
                from course_offering co join course c on c.id = co.course_id
                where c.code = ? and co.term = ?
                """, Long.class, code, term);
        assertThat(offeringId).isNotNull();
        assertThat(count("select count(*) from batch_task where task_type = 'COURSE_OFFERING_IMPORT' and operator = ?", admin))
                .isGreaterThanOrEqualTo(1);

        JsonNode offerings = json(get("/api/course-selection/offerings?term=" + term + "&page=1&size=10", login(student)), HttpStatus.OK);
        assertThat(offerings.at("/data/records").toString()).contains(code, "批量闭环课程");
        JsonNode teacherOfferings = json(get("/api/teacher/offerings?term=" + term, login(teacher)), HttpStatus.OK);
        assertThat(teacherOfferings.at("/data").toString()).contains(code, term, "批量任课教师-" + suffix);
        assertThat(count("select count(*) from operation_audit_log where action in ('CREATE_COURSE_BY_IMPORT','CREATE_OFFERING_BY_IMPORT') and operator = ?", admin))
                .isGreaterThanOrEqualTo(2);
    }

    @Test
    void offeringPreviewRejectsUnknownTeacherAndBadSchedule() throws Exception {
        String suffix = suffix();
        String admin = "batch_course_bad_admin_" + suffix;
        String code = "BCBAD" + suffix.toUpperCase();
        seedUser(admin, "批量课程管理员", List.of("ADMIN"), List.of("COURSE_WRITE"));
        jdbcTemplate.update("insert into course (code, name, credit, category) values (?, ?, ?, ?)",
                code, "坏数据课程", 2, "专业课");

        String badCsv = """
                courseCode,term,teacherName,capacity,scheduleText,classroom,selectionStartAt,selectionEndAt,status
                %s,2031-2032-1,不存在教师,20,not-a-schedule,A101,2031-09-30T23:59:59Z,2031-08-20T00:00:00Z,OPEN
                """.formatted(code);
        JsonNode preview = json(post("/api/admin/course-offerings/import-preview", login(admin), jsonContent(badCsv)), HttpStatus.OK);
        assertThat(preview.at("/data/errorRows").asInt()).isEqualTo(1);
        assertThat(preview.at("/data/errors").toString()).contains("教师不存在", "上课时间格式不可解析", "选课结束时间必须晚于开始时间");
    }

    private String jsonContent(String content) throws Exception {
        return objectMapper.writeValueAsString(Map.of("content", content));
    }

    private int count(String sql, Object... args) {
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, args);
        return count == null ? 0 : count;
    }
}
