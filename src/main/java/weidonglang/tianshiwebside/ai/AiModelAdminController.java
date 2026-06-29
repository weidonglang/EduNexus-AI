package weidonglang.tianshiwebside.ai;

import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import weidonglang.tianshiwebside.audit.AuditLogService;
import weidonglang.tianshiwebside.common.api.ApiResponse;
import weidonglang.tianshiwebside.common.trace.TraceIdHolder;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.Principal;
import java.time.Duration;
import java.util.List;

@RestController
@RequestMapping("/api/admin/ai")
@PreAuthorize("hasRole('ADMIN')")
public class AiModelAdminController {
    private final AiModelRegistryService modelRegistryService;
    private final AiSearchService searchService;
    private final AuditLogService auditLogService;
    private final HttpClient httpClient;

    public AiModelAdminController(
            AiModelRegistryService modelRegistryService,
            AiSearchService searchService,
            AuditLogService auditLogService
    ) {
        this.modelRegistryService = modelRegistryService;
        this.searchService = searchService;
        this.auditLogService = auditLogService;
        this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
    }

    @GetMapping("/models")
    public ApiResponse<List<AiModelRecord>> models() {
        return ApiResponse.success(modelRegistryService.models());
    }

    @PostMapping("/models")
    public ApiResponse<AiModelRecord> create(@Valid @RequestBody AiModelRequest request, Principal principal) {
        AiModelRecord model = modelRegistryService.create(request);
        audit(principal, "AI_MODEL_CREATE", model.id(), model.name());
        return ApiResponse.success(model);
    }

    @PutMapping("/models/{id}")
    public ApiResponse<AiModelRecord> update(@PathVariable Long id, @Valid @RequestBody AiModelRequest request, Principal principal) {
        AiModelRecord model = modelRegistryService.update(id, request);
        audit(principal, "AI_MODEL_UPDATE", id, model.name());
        return ApiResponse.success(model);
    }

    @PostMapping("/models/{id}/enable")
    public ApiResponse<Void> enable(@PathVariable Long id, Principal principal) {
        modelRegistryService.setEnabled(id, true);
        audit(principal, "AI_MODEL_ENABLE", id, "enabled");
        return ApiResponse.success();
    }

    @PostMapping("/models/{id}/disable")
    public ApiResponse<Void> disable(@PathVariable Long id, Principal principal) {
        modelRegistryService.setEnabled(id, false);
        audit(principal, "AI_MODEL_DISABLE", id, "disabled");
        return ApiResponse.success();
    }

    @DeleteMapping("/models/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id, Principal principal) {
        modelRegistryService.softDelete(id, operator(principal));
        audit(principal, "DELETE_AI_MODEL", id, "softDelete=true");
        return ApiResponse.success();
    }

    @PostMapping("/models/{id}/set-default")
    public ApiResponse<Void> setDefault(@PathVariable Long id, Principal principal) {
        modelRegistryService.setDefault(id);
        audit(principal, "AI_MODEL_SET_DEFAULT", id, "default");
        return ApiResponse.success();
    }

    @PostMapping("/models/{id}/test")
    public ApiResponse<AiSearchDtos.ModelTestResponse> testModel(@PathVariable Long id, Principal principal) {
        AiModelRecord model = modelRegistryService.require(id);
        long start = System.nanoTime();
        try {
            String provider = model.provider() == null ? "" : model.provider().toUpperCase();
            if (!provider.equals("OLLAMA")) {
                long latency = elapsed(start);
                modelRegistryService.recordCheck(id, "SKIPPED", latency, "当前仅对 OLLAMA 预设执行在线探测");
                audit(principal, "AI_MODEL_TEST", id, "SKIPPED:" + model.name());
                return ApiResponse.success(new AiSearchDtos.ModelTestResponse(true, "SKIPPED", latency, "非 OLLAMA 模型已保存配置，未发起探测。"));
            }
            String baseUrl = normalizeBaseUrl(model.baseUrl());
            HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + "/api/tags"))
                    .timeout(Duration.ofSeconds(5))
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            long latency = elapsed(start);
            boolean success = response.statusCode() >= 200 && response.statusCode() < 300;
            String status = success ? "UP" : "DOWN";
            String message = success ? "Ollama 可访问：" + model.modelName() : "Ollama 返回状态码 " + response.statusCode();
            modelRegistryService.recordCheck(id, status, latency, success ? null : message);
            audit(principal, "AI_MODEL_TEST", id, status + ":" + model.name());
            return ApiResponse.success(new AiSearchDtos.ModelTestResponse(success, status, latency, message));
        } catch (Exception ex) {
            long latency = elapsed(start);
            String message = ex.getClass().getSimpleName() + ": " + ex.getMessage();
            modelRegistryService.recordCheck(id, "DOWN", latency, message);
            auditLogService.record(operator(principal), "AI_MODEL_TEST", "AI_MODEL", id, message,
                    TraceIdHolder.get(), false, message);
            return ApiResponse.success(new AiSearchDtos.ModelTestResponse(false, "DOWN", latency, message));
        }
    }

    @GetMapping("/search/config")
    public ApiResponse<AiSearchDtos.SearchConfig> searchConfig() {
        return ApiResponse.success(searchService.config());
    }

    @GetMapping("/search/templates")
    public ApiResponse<List<AiSearchDtos.SearchConfigTemplate>> searchTemplates() {
        return ApiResponse.success(searchService.templates());
    }

    @PutMapping("/search/config")
    public ApiResponse<AiSearchDtos.SearchConfig> updateSearchConfig(
            @Valid @RequestBody AiSearchDtos.SearchConfigRequest request,
            Principal principal
    ) {
        AiSearchDtos.SearchConfig config = searchService.updateConfig(request);
        audit(principal, "AI_SEARCH_CONFIG_UPDATE", 1L, config.provider() + "; enabled=" + config.enabled());
        return ApiResponse.success(config);
    }

    @PostMapping("/search/test")
    public ApiResponse<AiSearchDtos.SearchTestResponse> testSearch(
            @Valid @RequestBody AiSearchDtos.SearchTestRequest request,
            Principal principal
    ) {
        AiSearchDtos.SearchTestResponse response = searchService.search(operator(principal), request.scene(), request.query());
        auditLogService.record(operator(principal), "AI_SEARCH_TEST", "AI_SEARCH", null,
                "allowed=" + response.allowed() + "; used=" + response.searchUsed() + "; query=" + request.query(),
                TraceIdHolder.get(), response.allowed(), response.message());
        return ApiResponse.success(response);
    }

    private void audit(Principal principal, String action, Object targetId, String detail) {
        auditLogService.record(operator(principal), action, "AI_MODEL", targetId, detail, TraceIdHolder.get());
    }

    private String operator(Principal principal) {
        return principal == null ? "anonymous" : principal.getName();
    }

    private long elapsed(long startNanos) {
        return Duration.ofNanos(System.nanoTime() - startNanos).toMillis();
    }

    private String normalizeBaseUrl(String baseUrl) {
        String value = baseUrl == null || baseUrl.isBlank() || baseUrl.startsWith("${")
                ? "http://localhost:11434"
                : baseUrl.trim();
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }
}
