package weidonglang.tianshiwebside.ai;

import jakarta.validation.constraints.NotBlank;

import java.time.Instant;
import java.util.List;

public class AiSearchDtos {
    public record SearchConfig(
            Long id,
            boolean enabled,
            String provider,
            String baseUrl,
            String apiKeyEnv,
            String allowedScenes,
            String safetyPolicy,
            String lastStatus,
            Long lastLatencyMs,
            String lastError,
            Instant lastTestedAt,
            Instant updatedAt
    ) {
    }

    public record SearchConfigRequest(
            boolean enabled,
            @NotBlank String provider,
            String baseUrl,
            String apiKeyEnv,
            String allowedScenes,
            String safetyPolicy
    ) {
    }

    public record SearchTestRequest(@NotBlank String query, String scene) {
    }

    public record SearchResult(String title, String link, String summary, Instant searchedAt) {
    }

    public record SearchTestResponse(
            boolean allowed,
            boolean searchUsed,
            String provider,
            String message,
            List<SearchResult> results,
            Long latencyMs
    ) {
    }

    public record ModelTestResponse(boolean success, String status, Long latencyMs, String message) {
    }

    public record SearchConfigTemplate(
            String code,
            String name,
            String description,
            String provider,
            boolean enabled,
            String baseUrl,
            String apiKeyEnv,
            String allowedScenes,
            String safetyPolicy,
            String method,
            String authMode,
            String testQuery,
            String resultMapping
    ) {
    }
}
