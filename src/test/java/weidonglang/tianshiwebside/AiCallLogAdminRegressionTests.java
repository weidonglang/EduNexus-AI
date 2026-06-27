package weidonglang.tianshiwebside;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AiCallLogAdminRegressionTests extends HttpRegressionTestSupport {
    @Test
    void adminCanFilterAiCallLogsAndStudentReceivesForbidden() throws Exception {
        String suffix = suffix();
        String admin = "v14_ai_admin_" + suffix;
        String student = "v14_ai_student_" + suffix;
        seedUser(admin, "AI 日志管理员", List.of("ADMIN"), List.of());
        seedStudent(student, "AI 日志学生");

        jdbcTemplate.update("""
                        insert into ai_call_log
                          (username, role_codes, function_type, prompt_summary, model_name, service_mode,
                           duration_ms, success, level, error_message, trace_id, created_at)
                        values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                student, "STUDENT", "CHAT", "请解释学籍异动", "qwen3:8b", "remote",
                120L, true, "INFO", null, "trace-info-" + suffix, Instant.now());
        jdbcTemplate.update("""
                        insert into ai_call_log
                          (username, role_codes, function_type, prompt_summary, model_name, service_mode,
                           duration_ms, success, level, error_message, trace_id, created_at)
                        values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                student, "STUDENT", "CHAT", "模拟失败", "local-fallback", "local-fallback",
                30L, false, "ERROR", "service offline", "trace-error-" + suffix, Instant.now());

        String adminToken = login(admin);
        JsonNode byUser = json(get("/api/admin/ai/call-logs?username=" + student, adminToken), HttpStatus.OK);
        assertThat(byUser.at("/data/total").asLong()).isGreaterThanOrEqualTo(2);

        JsonNode byFunction = json(get("/api/admin/ai/call-logs?functionType=CHAT", adminToken), HttpStatus.OK);
        assertThat(byFunction.at("/data/records").toString()).contains("trace-info-" + suffix);

        JsonNode bySuccess = json(get("/api/admin/ai/call-logs?success=false", adminToken), HttpStatus.OK);
        assertThat(bySuccess.at("/data/records/0/success").asBoolean()).isFalse();

        JsonNode byLevel = json(get("/api/admin/ai/call-logs?level=ERROR", adminToken), HttpStatus.OK);
        assertThat(byLevel.at("/data/records").toString()).contains("trace-error-" + suffix, "ERROR", "local-fallback");

        assertThat(get("/api/admin/ai/call-logs", login(student)).statusCode()).isEqualTo(HttpStatus.FORBIDDEN.value());
    }
}
