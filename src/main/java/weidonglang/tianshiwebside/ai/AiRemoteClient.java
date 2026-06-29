package weidonglang.tianshiwebside.ai;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component
public class AiRemoteClient {
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final String baseUrl;
    private final String serviceName;
    private final boolean discoveryEnabled;
    private final ObjectProvider<AiServiceFeignClient> feignClientProvider;

    public AiRemoteClient(
            @Value("${app.ai-service.base-url:http://localhost:8090}") String baseUrl,
            @Value("${app.ai-service.name:academic-ai-service}") String serviceName,
            @Value("${app.ai-service.discovery-enabled:false}") boolean discoveryEnabled,
            ObjectProvider<AiServiceFeignClient> feignClientProvider
    ) {
        this.objectMapper = new ObjectMapper().findAndRegisterModules();
        this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
        this.baseUrl = stripTrailingSlash(baseUrl);
        this.serviceName = serviceName;
        this.discoveryEnabled = discoveryEnabled;
        this.feignClientProvider = feignClientProvider;
    }

    public Optional<AiAssistantResponse> ask(String question, List<AiSourceDocument> documents) {
        try {
            if (discoveryEnabled) {
                return Optional.of(feign().ask(new AiServiceFeignClient.RagAnswerPayload(question, documents)));
            }
            Map<String, Object> payload = Map.of(
                    "question", question,
                    "documents", documents
            );
            String response = post("/internal/ai/rag/answer", payload, Duration.ofSeconds(90));
            return Optional.of(objectMapper.readValue(response, AiAssistantResponse.class));
        } catch (Exception ex) {
            return Optional.empty();
        }
    }

    public Optional<AiChatResponse> chat(String message) {
        return chat(message, null, null, null, "AUTO");
    }

    public Optional<AiChatResponse> chat(String message, Long selectedModelId, String selectedModelName, Long sessionId) {
        return chat(message, selectedModelId, selectedModelName, sessionId, "AUTO");
    }

    public Optional<AiChatResponse> chat(String message, Long selectedModelId, String selectedModelName, Long sessionId, String thinkingMode) {
        try {
            if (discoveryEnabled) {
                return Optional.of(feign().chat(new AiServiceFeignClient.ChatPayload(
                        message,
                        selectedModelName,
                        selectedModelId,
                        sessionId,
                        thinkingMode
                )));
            }
            Map<String, Object> payload = new java.util.LinkedHashMap<>();
            payload.put("message", message);
            payload.put("modelName", selectedModelName);
            payload.put("modelId", selectedModelId);
            payload.put("sessionId", sessionId);
            payload.put("thinkingMode", thinkingMode);
            String response = post("/internal/ai/chat", payload, Duration.ofSeconds(90));
            return Optional.of(objectMapper.readValue(response, AiChatResponse.class));
        } catch (Exception ex) {
            return Optional.empty();
        }
    }

    public Optional<LoadTestAnalysisResponse> analyzeLoadTest(Object report) {
        try {
            if (discoveryEnabled) {
                return Optional.of(feign().analyzeLoadTest(Map.of("report", report)));
            }
            String response = post("/internal/ai/load-test/analyze", Map.of("report", report), Duration.ofSeconds(180));
            return Optional.of(objectMapper.readValue(response, LoadTestAnalysisResponse.class));
        } catch (Exception ex) {
            return Optional.empty();
        }
    }

    public AiServiceStatusResponse status() {
        long start = System.nanoTime();
        try {
            Map<String, Object> raw;
            if (discoveryEnabled) {
                raw = feign().status();
            } else {
                String response = get("/internal/ai/status");
                raw = objectMapper.readValue(response, new TypeReference<>() {
                });
            }
            return new AiServiceStatusResponse(
                    true,
                    booleanValue(raw.get("ollamaEnabled")),
                    booleanValue(raw.get("ollamaReachable")),
                    String.valueOf(raw.getOrDefault("chatModel", "")),
                    String.valueOf(raw.getOrDefault("sqlModel", "")),
                    booleanValue(raw.get("ollamaReachable")) ? "AI 模式" : "本地兜底模式",
                    elapsedMillis(start),
                    String.valueOf(raw.getOrDefault("lastError", "")),
                    serviceName,
                    discoveryEnabled,
                    baseUrl,
                    "",
                    "",
                    "",
                    false,
                    "",
                    "",
                    Instant.now()
            );
        } catch (Exception ex) {
            return new AiServiceStatusResponse(
                    false,
                    false,
                    false,
                    "",
                    "",
                    "主系统本地兜底模式",
                    elapsedMillis(start),
                    friendlyAiError(ex),
                    serviceName,
                    discoveryEnabled,
                    baseUrl,
                    "",
                    "",
                    "",
                    false,
                    "",
                    "",
                    Instant.now()
            );
        }
    }

    public Optional<NaturalSqlGenerateResponse> generateSql(String question, List<SqlSchemaService.TableSchema> schemas) {
        try {
            Map<String, Object> raw;
            if (discoveryEnabled) {
                raw = feign().generateSql(new AiServiceFeignClient.SqlGeneratePayload(question, schemas));
            } else {
                Map<String, Object> payload = Map.of(
                        "question", question,
                        "schemas", schemas
                );
                String response = post("/internal/ai/sql/generate", payload, Duration.ofSeconds(90));
                raw = objectMapper.readValue(response, new TypeReference<>() {
                });
            }
            String sql = String.valueOf(raw.getOrDefault("sql", ""));
            String explanation = String.valueOf(raw.getOrDefault("explanation", ""));
            @SuppressWarnings("unchecked")
            List<String> warnings = raw.get("warnings") instanceof List<?> list
                    ? list.stream().map(String::valueOf).toList()
                    : List.of();
            return Optional.of(new NaturalSqlGenerateResponse(
                    sql,
                    explanation,
                    warnings,
                    schemas.stream().map(SqlSchemaService.TableSchema::tableName).toList(),
                    "ai-service"
            ));
        } catch (Exception ex) {
            return Optional.empty();
        }
    }

    private String post(String path, Object payload) throws Exception {
        return post(path, payload, Duration.ofSeconds(20));
    }

    private String post(String path, Object payload, Duration timeout) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + path))
                .timeout(timeout)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(payload)))
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException("ai-service status " + response.statusCode());
        }
        return response.body();
    }

    private String get(String path) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + path))
                .timeout(Duration.ofSeconds(5))
                .GET()
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException("ai-service status " + response.statusCode());
        }
        return response.body();
    }

    private AiServiceFeignClient feign() {
        AiServiceFeignClient client = feignClientProvider.getIfAvailable();
        if (client == null) {
            throw new IllegalStateException("ai-service OpenFeign client is unavailable");
        }
        return client;
    }

    private boolean booleanValue(Object value) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        return Boolean.parseBoolean(String.valueOf(value));
    }

    private long elapsedMillis(long startNanos) {
        return Duration.ofNanos(System.nanoTime() - startNanos).toMillis();
    }

    private String friendlyAiError(Exception ex) {
        String message = ex.getMessage() == null ? "" : ex.getMessage();
        if (message.contains("Load balancer does not contain an instance") || message.contains("No servers available")) {
            return "academic-ai-service 未在注册中心发现，已切换主系统本地兜底模式";
        }
        if (message.contains("Connection refused")) {
            return "academic-ai-service 连接失败，已切换主系统本地兜底模式";
        }
        return ex.getClass().getSimpleName() + ": " + message;
    }

    private String stripTrailingSlash(String value) {
        if (value == null || value.isBlank()) {
            return "http://localhost:8090";
        }
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }
}
