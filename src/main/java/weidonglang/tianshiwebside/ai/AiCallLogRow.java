package weidonglang.tianshiwebside.ai;

import java.time.Instant;

public record AiCallLogRow(
        Long id,
        String username,
        String roleCodes,
        String functionType,
        String promptSummary,
        String modelName,
        String serviceMode,
        Long durationMs,
        Boolean success,
        String level,
        String errorMessage,
        String traceId,
        Long sessionId,
        Long modelId,
        Instant createdAt
) {
}
