package weidonglang.tianshiwebside.ai;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import weidonglang.tianshiwebside.governance.ContentModerationService;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class AiSearchService {
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final ContentModerationService moderationService;

    public AiSearchService(JdbcTemplate jdbcTemplate, ContentModerationService moderationService) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = new ObjectMapper().findAndRegisterModules();
        this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
        this.moderationService = moderationService;
    }

    public AiSearchDtos.SearchConfig config() {
        return jdbcTemplate.queryForObject("""
                        select id, enabled, provider, base_url, api_key_env, allowed_scenes, safety_policy,
                               last_status, last_latency_ms, last_error, last_tested_at, updated_at
                        from ai_search_config
                        where id = 1
                        """,
                (rs, rowNum) -> new AiSearchDtos.SearchConfig(
                        rs.getLong("id"),
                        rs.getBoolean("enabled"),
                        rs.getString("provider"),
                        rs.getString("base_url"),
                        rs.getString("api_key_env"),
                        rs.getString("allowed_scenes"),
                        rs.getString("safety_policy"),
                        rs.getString("last_status"),
                        nullableLong(rs.getObject("last_latency_ms")),
                        rs.getString("last_error"),
                        instant(rs.getTimestamp("last_tested_at")),
                        rs.getTimestamp("updated_at").toInstant()
                ));
    }

    public AiSearchDtos.SearchConfig updateConfig(AiSearchDtos.SearchConfigRequest request) {
        jdbcTemplate.update("""
                        update ai_search_config
                        set enabled = ?, provider = ?, base_url = ?, api_key_env = ?, allowed_scenes = ?,
                            safety_policy = ?, updated_at = ?
                        where id = 1
                        """,
                request.enabled(),
                normalize(request.provider()),
                clean(request.baseUrl()),
                clean(request.apiKeyEnv()),
                clean(request.allowedScenes()),
                clean(request.safetyPolicy()),
                Instant.now());
        return config();
    }

    public List<AiSearchDtos.SearchConfigTemplate> templates() {
        return List.of(
                new AiSearchDtos.SearchConfigTemplate(
                        "DISABLED",
                        "关闭联网搜索",
                        "完全关闭外部搜索，仅使用本地 AI 和知识库兜底。",
                        "LOCAL_DEMO",
                        false,
                        "",
                        "",
                        "CHAT,ASSISTANT,TECHNICAL",
                        "关闭搜索时仍保留敏感内容拦截和 AI 输入输出安全审查。",
                        "GET",
                        "NONE",
                        "Academic-Nexus test",
                        "not applicable"
                ),
                new AiSearchDtos.SearchConfigTemplate(
                        "LOCAL_DEMO",
                        "本地模拟搜索",
                        "不访问公网，返回内置样例结果，适合课堂演示和验收测试。",
                        "LOCAL_DEMO",
                        true,
                        "",
                        "",
                        "CHAT,ASSISTANT,TECHNICAL",
                        "禁止个人数据、成绩、SQL、密码、密钥等敏感问题自动联网搜索；搜索结果继续进入安全审查。",
                        "GET",
                        "NONE",
                        "Spring Cloud Alibaba Nacos Discovery 最新用法",
                        "内置 title/link/summary"
                ),
                new AiSearchDtos.SearchConfigTemplate(
                        "OPENAI_COMPATIBLE",
                        "OpenAI-compatible Search API",
                        "用于兼容返回 results 数组的搜索服务，认证通过 Bearer API Key。",
                        "CUSTOM",
                        true,
                        "https://search.example.com/v1/search?query={query}",
                        "SEARCH_API_KEY",
                        "CHAT,ASSISTANT,TECHNICAL",
                        "仅允许非个人、非敏感、非 SQL 类问题联网；结果字段按 title/url/content 读取。",
                        "GET",
                        "BEARER_ENV",
                        "Academic-Nexus test",
                        "results[].title, results[].url, results[].content"
                ),
                new AiSearchDtos.SearchConfigTemplate(
                        "SEARCH_ENGINE_COMPATIBLE",
                        "Tavily / SerpAPI / Bing Web Search-compatible",
                        "用于常见搜索聚合 API，可通过 {query} 占位或 q 参数拼接请求。",
                        "CUSTOM",
                        true,
                        "https://api.example.com/search?q={query}",
                        "SEARCH_API_KEY",
                        "CHAT,ASSISTANT,TECHNICAL",
                        "开启安全模式，搜索前过滤敏感问题，搜索后审查结果摘要。",
                        "GET",
                        "BEARER_ENV",
                        "Academic-Nexus Docker deployment",
                        "results[].title, results[].url/link, results[].content/summary"
                ),
                new AiSearchDtos.SearchConfigTemplate(
                        "CUSTOM_HTTP",
                        "自定义 HTTP 搜索接口",
                        "适配自建 SearXNG 或内部搜索网关，后端期望返回 JSON results 数组。",
                        "CUSTOM",
                        true,
                        "http://searxng-core:8080/search?q={query}&format=json",
                        "",
                        "CHAT,ASSISTANT,TECHNICAL",
                        "自定义接口也必须经过搜索前安全过滤和搜索结果安全审查。",
                        "GET",
                        "OPTIONAL_BEARER_ENV",
                        "Academic-Nexus test",
                        "results[].title, results[].url/link, results[].content/summary"
                )
        );
    }

    public AiSearchDtos.SearchTestResponse search(String username, String scene, String query) {
        AiSearchDtos.SearchConfig current = config();
        String safeScene = scene == null || scene.isBlank() ? "ADMIN_TEST" : scene.trim().toUpperCase(Locale.ROOT);
        SafetyDecision decision = safetyDecision(query, safeScene);
        if (!decision.allowed()) {
            log(username, safeScene, query, current.provider(), 0, true, decision.message());
            return new AiSearchDtos.SearchTestResponse(false, false, current.provider(), decision.message(), List.of(), null);
        }
        if (!current.enabled()) {
            return new AiSearchDtos.SearchTestResponse(true, false, current.provider(), "联网搜索未启用", List.of(), null);
        }
        long start = System.nanoTime();
        try {
            List<AiSearchDtos.SearchResult> results = fetch(current, query);
            for (AiSearchDtos.SearchResult result : results) {
                moderationService.checkConfigured("SEARCH_RESULT",
                        result.title() + "\n" + result.summary() + "\n" + result.link(),
                        username == null ? "anonymous" : username);
            }
            long latency = Duration.ofNanos(System.nanoTime() - start).toMillis();
            recordSearchCheck("UP", latency, null);
            log(username, safeScene, query, current.provider(), results.size(), false, null);
            return new AiSearchDtos.SearchTestResponse(true, true, current.provider(), "搜索完成", results, latency);
        } catch (Exception ex) {
            long latency = Duration.ofNanos(System.nanoTime() - start).toMillis();
            String message = ex.getClass().getSimpleName() + ": " + ex.getMessage();
            recordSearchCheck("DOWN", latency, message);
            log(username, safeScene, query, current.provider(), 0, false, message);
            return new AiSearchDtos.SearchTestResponse(true, false, current.provider(), message, List.of(), latency);
        }
    }

    public boolean shouldSearch(String message) {
        String q = message == null ? "" : message.toLowerCase(Locale.ROOT);
        return q.contains("最新") || q.contains("联网") || q.contains("搜索")
                || q.contains("spring cloud") || q.contains("nacos") || q.contains("政策");
    }

    public SafetyDecision safetyDecision(String query, String scene) {
        String q = query == null ? "" : query.toLowerCase(Locale.ROOT);
        if (q.isBlank()) {
            return new SafetyDecision(false, "搜索问题不能为空");
        }
        String joined = q + " " + (scene == null ? "" : scene.toLowerCase(Locale.ROOT));
        if (joined.contains("sql") || joined.contains("select ") || joined.contains("密码")
                || joined.contains("password") || joined.contains("token") || joined.contains("密钥")
                || joined.contains("身份证") || joined.contains("成绩") || joined.contains("学号")
                || joined.contains("个人") || joined.contains("手机号") || joined.contains("邮箱")) {
            return new SafetyDecision(false, "该问题涉及个人数据、SQL 或敏感信息，已禁止自动联网搜索");
        }
        return new SafetyDecision(true, "允许搜索");
    }

    private List<AiSearchDtos.SearchResult> fetch(AiSearchDtos.SearchConfig config, String query) throws Exception {
        String provider = normalize(config.provider());
        if (provider.equals("LOCAL_DEMO")) {
            return List.of(new AiSearchDtos.SearchResult(
                    "Spring Cloud Alibaba Nacos Discovery",
                    "https://sca.aliyun.com/en/docs/2023/user-guide/nacos/quick-start/",
                    "本地演示搜索结果：Nacos Discovery 用于服务注册发现，主系统可通过服务名调用 ai-service。",
                    Instant.now()
            ));
        }
        if (config.baseUrl() == null || config.baseUrl().isBlank()) {
            throw new IllegalStateException("搜索服务 baseUrl 未配置");
        }
        URI uri = URI.create(searchUrl(config.baseUrl(), query));
        HttpRequest.Builder builder = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(8))
                .header("Accept", "application/json")
                .GET();
        String apiKey = apiKey(config.apiKeyEnv());
        if (!apiKey.isBlank()) {
            builder.header("Authorization", "Bearer " + apiKey);
        }
        HttpResponse<String> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException("搜索服务返回 " + response.statusCode());
        }
        Map<String, Object> raw = objectMapper.readValue(response.body(), new TypeReference<>() {
        });
        Object results = raw.get("results");
        if (!(results instanceof List<?> list)) {
            return List.of();
        }
        return list.stream()
                .filter(Map.class::isInstance)
                .map(Map.class::cast)
                .limit(5)
                .map(item -> new AiSearchDtos.SearchResult(
                        stringValue(item.get("title")),
                        stringValue(item.getOrDefault("url", item.get("link"))),
                        stringValue(item.getOrDefault("content", item.get("summary"))),
                        Instant.now()
                ))
                .toList();
    }

    private String searchUrl(String baseUrl, String query) {
        String encoded = URLEncoder.encode(query, StandardCharsets.UTF_8);
        String separator = baseUrl.contains("?") ? "&" : "?";
        if (baseUrl.contains("{query}")) {
            return baseUrl.replace("{query}", encoded);
        }
        return baseUrl + separator + "q=" + encoded + "&format=json";
    }

    private void recordSearchCheck(String status, Long latencyMs, String error) {
        jdbcTemplate.update("""
                        update ai_search_config
                        set last_status = ?, last_latency_ms = ?, last_error = ?, last_tested_at = ?, updated_at = ?
                        where id = 1
                        """,
                status, latencyMs, error, Instant.now(), Instant.now());
    }

    private void log(String username, String scene, String query, String provider, int resultCount, boolean blocked, String reason) {
        jdbcTemplate.update("""
                        insert into ai_search_result_log
                          (username, scene, query_text, provider, result_count, blocked, block_reason, created_at)
                        values (?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                username == null ? "anonymous" : username,
                scene,
                truncate(query, 500),
                provider,
                resultCount,
                blocked,
                truncate(reason, 240),
                Instant.now());
    }

    private String apiKey(String envName) {
        if (envName == null || envName.isBlank()) {
            return "";
        }
        String value = System.getenv(envName.trim());
        return value == null ? "" : value.trim();
    }

    private String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    private String normalize(String value) {
        return clean(value).toUpperCase(Locale.ROOT);
    }

    private String clean(String value) {
        return value == null ? "" : value.trim();
    }

    private Instant instant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }

    private Long nullableLong(Object value) {
        return value == null ? null : ((Number) value).longValue();
    }

    public record SafetyDecision(boolean allowed, String message) {
    }
}
