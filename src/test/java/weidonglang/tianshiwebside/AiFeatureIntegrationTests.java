package weidonglang.tianshiwebside;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.TestingAuthenticationToken;
import weidonglang.tianshiwebside.ai.AcademicProfileService;
import weidonglang.tianshiwebside.ai.AiAssistantService;
import weidonglang.tianshiwebside.ai.AiModelRegistryService;
import weidonglang.tianshiwebside.ai.AiServiceFeignClient;
import weidonglang.tianshiwebside.ai.AiSearchService;
import weidonglang.tianshiwebside.ai.NaturalSqlService;
import weidonglang.tianshiwebside.common.error.BusinessException;
import weidonglang.tianshiwebside.governance.ContentModerationService;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class AiFeatureIntegrationTests {
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private AiAssistantService assistantService;
    @Autowired
    private NaturalSqlService naturalSqlService;
    @Autowired
    private AcademicProfileService academicProfileService;
    @Autowired
    private AiModelRegistryService modelRegistryService;
    @Autowired
    private AiSearchService searchService;
    @Autowired
    private ContentModerationService moderationService;
    @Autowired
    private ApplicationContext applicationContext;
    @Autowired
    private Environment environment;

    @Test
    void ragRefusesOffTopicAndPermissionOutOfScopeQuestions() {
        var offTopic = assistantService.ask("帮我写一首爱情诗", auth("student_ai"));
        var sensitive = assistantService.ask("查询其他学生的成绩和密码", auth("student_ai"));

        assertThat(offTopic.answerType()).isEqualTo("REFUSAL");
        assertThat(offTopic.refusalReason()).contains("非教务");
        assertThat(sensitive.answerType()).isEqualTo("REFUSAL");
        assertThat(sensitive.refusalReason()).contains("敏感");
    }

    @Test
    void ragReturnsEvidenceSourcesForTeachingAffairsQuestion() {
        var response = assistantService.ask("重修申请需要什么条件？", auth("student_ai"));

        assertThat(response.answerType()).isEqualTo("ANSWER");
        assertThat(response.sources()).isNotEmpty();
        assertThat(response.sources()).anyMatch(source -> source.title().contains("重修") || source.content().contains("重修"));
    }

    @Test
    void naturalSqlRejectsUnsafeGenerationAndExecution() {
        assertThatThrownBy(() -> naturalSqlService.generate("删除所有学生数据", adminAuth()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("只读");
        assertThatThrownBy(() -> naturalSqlService.generate("查询所有用户 token", adminAuth()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("敏感");
        assertThatThrownBy(() -> naturalSqlService.execute("select * from student; drop table student", adminAuth()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("多语句");
        assertThatThrownBy(() -> naturalSqlService.execute("update academic_grade set score = 100", adminAuth()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("SELECT");
    }

    @Test
    void naturalSqlExecutesSafeSelectAndWritesAiLog() {
        String suffix = suffix();
        jdbcTemplate.update("insert into course (code, name, credit, category) values (?, ?, ?, ?)",
                "AIT" + suffix, "AI SQL 测试课程", 3, "专业必修");

        var result = naturalSqlService.execute("select code, name from course where code = 'AIT" + suffix + "'", adminAuth());
        Integer logCount = jdbcTemplate.queryForObject("select count(*) from ai_call_log where function_type = 'SQL_EXECUTE'", Integer.class);

        assertThat(result.sql()).contains("limit 100");
        assertThat(result.rows()).hasSize(1);
        assertThat(logCount).isNotNull().isPositive();
    }

    @Test
    void academicProfileSummarizesCreditRiskAndAuditItems() {
        String username = "ai_profile_" + suffix();
        seedStudent(username);
        Long studentId = jdbcTemplate.queryForObject("""
                select s.id
                from student s
                join sys_user u on u.id = s.user_id
                where u.username = ?
                """, Long.class, username);
        Long courseId = seedCourse("AIP" + suffix(), "AI 画像课程", 3);
        jdbcTemplate.update("insert into academic_grade (student_id, course_id, term, score, grade_point, exam_type) values (?, ?, ?, ?, ?, ?)",
                studentId, courseId, "2026-2027-1", 55, "0.00", "正常考试");
        jdbcTemplate.update("insert into teaching_plan_item (grade, major, term, course_code, course_name, credit, course_type, assessment_type) values (?, ?, ?, ?, ?, ?, ?, ?)",
                "2026", "AI测试专业", "2026-2027-1", "AIPLAN", "培养计划课程", 20, "专业必修", "考试");
        jdbcTemplate.update("insert into graduation_audit (student_id, audit_item, required_value, current_value, passed, remark, updated_at) values (?, ?, ?, ?, ?, ?, ?)",
                studentId, "总学分", "20", "0", false, "学分不足", Instant.now());

        var profile = academicProfileService.currentProfile(auth(username));

        assertThat(profile.studentNo()).isEqualTo(username);
        assertThat(profile.failedCourseCount()).isEqualTo(1);
        assertThat(profile.graduationRiskLevel()).isIn("中风险", "高风险");
        assertThat(profile.aiSuggestion()).contains("风险");
    }

    @Test
    void modelRegistryContainsPresetsAndSearchBlocksSensitiveQueries() {
        assertThat(modelRegistryService.models())
                .anyMatch(model -> model.name().equals("Qwythos-9B-Claude-Mythos-5-1M"))
                .anyMatch(model -> model.modelType().equals("SQL") && model.defaultModel());
        assertThat(modelRegistryService.defaultModelName("CHAT", "fallback")).isEqualTo("qwen3:8b");

        var response = searchService.search("admin_ai", "CHAT", "联网搜索某个学生成绩和学号");
        Integer blocked = jdbcTemplate.queryForObject("select count(*) from ai_search_result_log where blocked = true", Integer.class);

        assertThat(response.allowed()).isFalse();
        assertThat(response.message()).contains("敏感");
        assertThat(blocked).isNotNull().isPositive();
    }

    @Test
    void aiSafetyConfigCanBlockConfiguredScenes() {
        var config = moderationService.updateSafetyConfig("ROUND1_TEST", true, "block", "测试策略");

        assertThat(config.scene()).isEqualTo("ROUND1_TEST");
        assertThat(config.strategy()).isEqualTo("block");
        assertThatThrownBy(() -> moderationService.checkConfigured("ROUND1_TEST", "这里包含示例敏感词A", "admin_ai"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("拦截");
        Integer hitCount = jdbcTemplate.queryForObject("""
                select count(*)
                from content_moderation_log
                where scene = 'ROUND1_TEST'
                  and action = 'BLOCK'
                """, Integer.class);
        assertThat(hitCount).isNotNull().isPositive();
    }

    @Test
    void aiSafetyStrategiesRecordWarnReviewAndLogOnlyWithoutBlocking() {
        moderationService.updateSafetyConfig("ROUND1_WARN", true, "warn", "warn strategy");
        moderationService.updateSafetyConfig("ROUND1_REVIEW", true, "review", "review strategy");
        moderationService.updateSafetyConfig("ROUND1_LOG", true, "log_only", "log strategy");

        assertThat(moderationService.checkConfigured("ROUND1_WARN", "示例敏感词A", "admin_ai").action()).isEqualTo("WARN");
        assertThat(moderationService.checkConfigured("ROUND1_REVIEW", "示例敏感词A", "admin_ai").action()).isEqualTo("REVIEW");
        assertThat(moderationService.checkConfigured("ROUND1_LOG", "示例敏感词A", "admin_ai").action()).isEqualTo("LOG_ONLY");
    }

    @Test
    void springCloudFeignClientIsRegisteredForAiServiceName() {
        assertThat(applicationContext.getBean(AiServiceFeignClient.class)).isNotNull();
        assertThat(environment.getProperty("app.ai-service.name")).isEqualTo("academic-ai-service");
        assertThat(environment.getProperty("spring.cloud.nacos.discovery.server-addr")).isNotBlank();
    }

    private void seedStudent(String username) {
        jdbcTemplate.update("insert into sys_user (username, password_hash, display_name, status) values (?, ?, ?, ?)",
                username, "{noop}123456", username, "ACTIVE");
        Long userId = jdbcTemplate.queryForObject("select id from sys_user where username = ?", Long.class, username);
        jdbcTemplate.update("""
                        insert into student (user_id, student_no, college, major, class_name, grade, status, phone, email, address)
                        values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                userId, username, "AI学院", "AI测试专业", "AI测试班", "2026", "在籍", "13800000000", username + "@example.com", "天津");
    }

    private Long seedCourse(String code, String name, int credit) {
        jdbcTemplate.update("insert into course (code, name, credit, category) values (?, ?, ?, ?)",
                code, name, credit, "专业必修");
        return jdbcTemplate.queryForObject("select id from course where code = ?", Long.class, code);
    }

    private TestingAuthenticationToken auth(String username) {
        return new TestingAuthenticationToken(username, null, "ROLE_STUDENT");
    }

    private TestingAuthenticationToken adminAuth() {
        return new TestingAuthenticationToken("admin_ai", null, "ROLE_ADMIN");
    }

    private String suffix() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    }
}
