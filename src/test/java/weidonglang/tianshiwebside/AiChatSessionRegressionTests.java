package weidonglang.tianshiwebside;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AiChatSessionRegressionTests extends HttpRegressionTestSupport {
    @Test
    void chatSessionsPersistMessagesRespectOwnershipAndFilterDisabledModels() throws Exception {
        String suffix = suffix();
        String userA = "v14_chat_a_" + suffix;
        String userB = "v14_chat_b_" + suffix;
        seedStudent(userA, "聊天学生 A");
        seedStudent(userB, "聊天学生 B");

        String tokenA = login(userA);
        String tokenB = login(userB);

        JsonNode models = json(get("/api/ai/chat/models", tokenA), HttpStatus.OK);
        assertThat(models.at("/data").isArray()).isTrue();
        assertThat(models.at("/data").toString()).contains("CHAT").doesNotContain("Qwythos");
        Long modelId = models.at("/data/0/id").asLong();

        JsonNode session = json(post("/api/ai/chat/sessions", tokenA, """
                {"title":"V14 回归会话","modelId":%d}
                """.formatted(modelId)), HttpStatus.OK);
        long sessionId = session.at("/data/id").asLong();
        assertThat(sessionId).isPositive();

        JsonNode send = json(post("/api/ai/chat/sessions/" + sessionId + "/messages", tokenA, """
                {"content":"请介绍一下平台功能","modelId":%d}
                """.formatted(modelId)), HttpStatus.OK);
        assertThat(send.at("/data/messages").size()).isGreaterThanOrEqualTo(2);
        assertThat(send.at("/data/messages").toString()).contains("平台功能");
        assertThat(send.at("/data/response/answer").asText()).isNotBlank();

        JsonNode messages = json(get("/api/ai/chat/sessions/" + sessionId + "/messages", tokenA), HttpStatus.OK);
        assertThat(messages.at("/data").size()).isGreaterThanOrEqualTo(2);

        JsonNode sessions = json(get("/api/ai/chat/sessions", tokenA), HttpStatus.OK);
        assertThat(sessions.at("/data").toString()).contains("V14 回归会话");

        assertThat(get("/api/ai/chat/sessions/" + sessionId + "/messages", tokenB).statusCode())
                .isEqualTo(HttpStatus.NOT_FOUND.value());

        Long disabledModelId = jdbcTemplate.queryForObject("""
                select id
                from ai_model_registry
                where enabled = false and model_type = 'CHAT'
                limit 1
                """, Long.class);
        assertThat(post("/api/ai/chat/sessions", tokenA, """
                {"title":"禁用模型","modelId":%d}
                """.formatted(disabledModelId)).statusCode()).isEqualTo(HttpStatus.BAD_REQUEST.value());

        Integer logCount = jdbcTemplate.queryForObject("""
                select count(*)
                from ai_call_log
                where username = ? and session_id = ? and model_id = ?
                """, Integer.class, userA, sessionId, modelId);
        assertThat(logCount).isNotNull().isGreaterThan(0);
    }
}
