package weidonglang.tianshiwebside;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AiModelDeleteRegressionTests extends HttpRegressionTestSupport {
    @Test
    void adminCanSoftDeleteDisabledModelAndHistoryLogsRemainReadable() throws Exception {
        String suffix = suffix();
        String admin = "ai_model_admin_" + suffix;
        seedUser(admin, "AI 模型管理员", List.of("ADMIN"), List.of());
        String token = login(admin);

        JsonNode created = json(post("/api/admin/ai/models", token, """
                {
                  "name":"删除回归模型-%s",
                  "provider":"CUSTOM",
                  "modelName":"delete-regression-%s",
                  "baseUrl":"",
                  "apiKeyRef":"",
                  "modelType":"CHAT",
                  "purpose":"删除回归",
                  "enabled":false,
                  "defaultModel":false,
                  "description":"用于验证软删除"
                }
                """.formatted(suffix, suffix)), HttpStatus.OK);
        long modelId = created.at("/data/id").asLong();
        String modelName = created.at("/data/name").asText();
        jdbcTemplate.update("""
                        insert into ai_call_log
                          (username, role_codes, function_type, prompt_summary, model_name, duration_ms, success,
                           service_mode, level, trace_id, model_id, selected_model_name, actual_model_name, created_at)
                        values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                admin, "ADMIN", "CHAT", "历史日志", modelName, 12L, true,
                "local", "INFO", "trace-delete-" + suffix, modelId, modelName, modelName, Instant.now());

        assertThat(delete("/api/admin/ai/models/" + modelId, token).statusCode()).isEqualTo(HttpStatus.OK.value());
        JsonNode models = json(get("/api/admin/ai/models", token), HttpStatus.OK);
        assertThat(models.at("/data").toString()).doesNotContain(modelName);
        assertThat(count("select count(*) from ai_model_registry where id = ? and deleted = true", modelId)).isEqualTo(1);
        assertThat(count("select count(*) from ai_call_log where model_id = ? and model_name = ?", modelId, modelName)).isEqualTo(1);
        assertThat(count("select count(*) from operation_audit_log where action = 'DELETE_AI_MODEL' and target_id = ?", String.valueOf(modelId)))
                .isGreaterThanOrEqualTo(1);
    }

    @Test
    void defaultDeleteIsRejectedEnabledNonDefaultCanBeSoftDeletedAndNonAdminDeleteIsRejected() throws Exception {
        String suffix = suffix();
        String admin = "ai_model_guard_admin_" + suffix;
        String student = "ai_model_guard_student_" + suffix;
        seedUser(admin, "AI 模型管理员", List.of("ADMIN"), List.of());
        seedStudent(student, "AI 模型普通学生");
        String adminToken = login(admin);

        JsonNode models = json(get("/api/admin/ai/models", adminToken), HttpStatus.OK);
        long defaultModelId = -1L;
        for (JsonNode model : models.at("/data")) {
            if (model.at("/defaultModel").asBoolean()) {
                defaultModelId = model.at("/id").asLong();
                break;
            }
        }
        assertThat(defaultModelId).isPositive();
        assertThat(delete("/api/admin/ai/models/" + defaultModelId, adminToken).statusCode())
                .isEqualTo(HttpStatus.CONFLICT.value());

        JsonNode enabled = json(post("/api/admin/ai/models", adminToken, """
                {
                  "name":"启用保护模型-%s",
                  "provider":"CUSTOM",
                  "modelName":"enabled-guard-%s",
                  "baseUrl":"",
                  "apiKeyRef":"",
                  "modelType":"CHAT",
                  "purpose":"删除保护",
                  "enabled":true,
                  "defaultModel":false,
                  "description":"启用非默认模型可直接软删除"
                }
                """.formatted(suffix, suffix)), HttpStatus.OK);
        long enabledModelId = enabled.at("/data/id").asLong();
        assertThat(delete("/api/admin/ai/models/" + enabledModelId, adminToken).statusCode())
                .isEqualTo(HttpStatus.OK.value());
        assertThat(count("select count(*) from ai_model_registry where id = ? and deleted = true and enabled = false and is_default = false", enabledModelId))
                .isEqualTo(1);

        JsonNode disabled = json(post("/api/admin/ai/models", adminToken, """
                {
                  "name":"无权删除模型-%s",
                  "provider":"CUSTOM",
                  "modelName":"forbidden-delete-%s",
                  "baseUrl":"",
                  "apiKeyRef":"",
                  "modelType":"CHAT",
                  "purpose":"权限保护",
                  "enabled":false,
                  "defaultModel":false,
                  "description":"普通用户不能删除"
                }
                """.formatted(suffix, suffix)), HttpStatus.OK);
        assertThat(delete("/api/admin/ai/models/" + disabled.at("/data/id").asLong(), login(student)).statusCode())
                .isEqualTo(HttpStatus.FORBIDDEN.value());
    }

    private int count(String sql, Object... args) {
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, args);
        return count == null ? 0 : count;
    }
}
