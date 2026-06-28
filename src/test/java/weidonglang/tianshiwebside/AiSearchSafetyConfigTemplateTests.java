package weidonglang.tianshiwebside;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AiSearchSafetyConfigTemplateTests extends HttpRegressionTestSupport {
    @Test
    void searchTemplatesCanApplyLocalDemoAndReturnObservableResults() throws Exception {
        String suffix = suffix();
        String admin = "ai_template_admin_" + suffix;
        seedUser(admin, "AI 配置管理员", List.of("ADMIN"), List.of());
        String token = login(admin);

        JsonNode templates = json(get("/api/admin/ai/search/templates", token), HttpStatus.OK);
        assertThat(templates.at("/data").toString()).contains("LOCAL_DEMO", "CUSTOM_HTTP", "SEARCH_API_KEY");

        json(put("/api/admin/ai/search/config", token, """
                {
                  "enabled":true,
                  "provider":"LOCAL_DEMO",
                  "baseUrl":"",
                  "apiKeyEnv":"",
                  "allowedScenes":"CHAT,ASSISTANT,TECHNICAL",
                  "safetyPolicy":"本地模拟搜索模板回归测试"
                }
                """), HttpStatus.OK);
        JsonNode test = json(post("/api/admin/ai/search/test", token, """
                {"query":"Spring Cloud Alibaba Nacos Discovery 最新用法","scene":"ADMIN_TEST"}
                """), HttpStatus.OK);
        assertThat(test.at("/data/searchUsed").asBoolean()).isTrue();
        assertThat(test.at("/data/results").toString()).contains("Nacos Discovery");

        JsonNode blocked = json(post("/api/admin/ai/search/test", token, """
                {"query":"查询学生成绩和密码","scene":"ADMIN_TEST"}
                """), HttpStatus.OK);
        assertThat(blocked.at("/data/allowed").asBoolean()).isFalse();
        assertThat(blocked.at("/data/message").asText()).contains("敏感信息");
    }

    @Test
    void safetyTemplatesAndTestsExposeBlockAndLogOnlyOutcomes() throws Exception {
        String suffix = suffix();
        String admin = "ai_safety_admin_" + suffix;
        String student = "ai_safety_student_" + suffix;
        seedUser(admin, "AI 安全管理员", List.of("ADMIN"), List.of());
        seedStudent(student, "AI 安全普通学生");
        String token = login(admin);

        JsonNode templates = json(get("/api/admin/ai/safety/templates", token), HttpStatus.OK);
        assertThat(templates.at("/data").toString()).contains("STRICT", "LOG_ONLY", "关闭模式");

        json(put("/api/admin/ai/safety/config", token, """
                {"configs":[{"scene":"AI_INPUT","enabled":true,"strategy":"block","description":"strict regression"}]}
                """), HttpStatus.OK);
        JsonNode strict = json(post("/api/admin/ai/safety/test", token, """
                {"scene":"AI_INPUT","content":"这里包含示例敏感词A"}
                """), HttpStatus.OK);
        assertThat(strict.at("/data/blocked").asBoolean()).isTrue();
        assertThat(strict.at("/data/action").asText()).isEqualTo("BLOCK");

        json(put("/api/admin/ai/safety/config", token, """
                {"configs":[{"scene":"AI_INPUT","enabled":true,"strategy":"log_only","description":"log only regression"}]}
                """), HttpStatus.OK);
        JsonNode logOnly = json(post("/api/admin/ai/safety/test", token, """
                {"scene":"AI_INPUT","content":"这里包含示例敏感词A"}
                """), HttpStatus.OK);
        assertThat(logOnly.at("/data/success").asBoolean()).isTrue();
        assertThat(logOnly.at("/data/action").asText()).isEqualTo("LOG_ONLY");

        assertThat(post("/api/admin/ai/safety/test", login(student), """
                {"scene":"AI_INPUT","content":"普通用户无权测试"}
                """).statusCode()).isEqualTo(HttpStatus.FORBIDDEN.value());
    }
}
