package weidonglang.tianshiwebside;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AiModelRegistryStateTests extends HttpRegressionTestSupport {
    @Test
    void defaultEnabledDeletedStateStaysConsistent() throws Exception {
        String suffix = suffix();
        String admin = "ai_state_admin_" + suffix;
        seedUser(admin, "AI 状态管理员", List.of("ADMIN"), List.of());
        String token = login(admin);

        JsonNode first = json(post("/api/admin/ai/models", token, """
                {
                  "name":"默认状态模型 A-%s",
                  "provider":"CUSTOM",
                  "modelName":"state-a-%s",
                  "baseUrl":"",
                  "apiKeyRef":"",
                  "modelType":"CHAT",
                  "purpose":"状态测试",
                  "enabled":true,
                  "defaultModel":false,
                  "description":"A"
                }
                """.formatted(suffix, suffix)), HttpStatus.OK);
        long firstId = first.at("/data/id").asLong();

        assertThat(post("/api/admin/ai/models/" + firstId + "/disable", token, "").statusCode())
                .isEqualTo(HttpStatus.OK.value());
        assertThat(post("/api/admin/ai/models/" + firstId + "/set-default", token, "").statusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST.value());
        assertThat(put("/api/admin/ai/models/" + firstId, token, """
                {
                  "name":"默认状态模型 A-%s",
                  "provider":"CUSTOM",
                  "modelName":"state-a-%s",
                  "baseUrl":"",
                  "apiKeyRef":"",
                  "modelType":"CHAT",
                  "purpose":"状态测试",
                  "enabled":false,
                  "defaultModel":true,
                  "description":"不能默认"
                }
                """.formatted(suffix, suffix)).statusCode()).isEqualTo(HttpStatus.BAD_REQUEST.value());

        JsonNode second = json(post("/api/admin/ai/models", token, """
                {
                  "name":"默认状态模型 B-%s",
                  "provider":"CUSTOM",
                  "modelName":"state-b-%s",
                  "baseUrl":"",
                  "apiKeyRef":"",
                  "modelType":"CHAT",
                  "purpose":"状态测试",
                  "enabled":true,
                  "defaultModel":true,
                  "description":"B"
                }
                """.formatted(suffix, suffix)), HttpStatus.OK);
        long secondId = second.at("/data/id").asLong();
        assertThat(post("/api/admin/ai/models/" + secondId + "/disable", token, "").statusCode())
                .isEqualTo(HttpStatus.CONFLICT.value());
        assertThat(delete("/api/admin/ai/models/" + secondId, token).statusCode())
                .isEqualTo(HttpStatus.CONFLICT.value());

        JsonNode third = json(post("/api/admin/ai/models", token, """
                {
                  "name":"默认状态模型 C-%s",
                  "provider":"CUSTOM",
                  "modelName":"state-c-%s",
                  "baseUrl":"",
                  "apiKeyRef":"",
                  "modelType":"CHAT",
                  "purpose":"状态测试",
                  "enabled":true,
                  "defaultModel":true,
                  "description":"C"
                }
                """.formatted(suffix, suffix)), HttpStatus.OK);
        long thirdId = third.at("/data/id").asLong();
        assertThat(delete("/api/admin/ai/models/" + secondId, token).statusCode()).isEqualTo(HttpStatus.OK.value());
        assertThat(count("select count(*) from ai_model_registry where id = ? and deleted = true and enabled = false and is_default = false", secondId))
                .isEqualTo(1);
        assertThat(count("select count(*) from ai_model_registry where model_type = 'CHAT' and deleted = false and is_default = true"))
                .isEqualTo(1);
        assertThat(count("select count(*) from ai_model_registry where id = ? and is_default = true", thirdId)).isEqualTo(1);
    }

    private int count(String sql, Object... args) {
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, args);
        return count == null ? 0 : count;
    }
}
