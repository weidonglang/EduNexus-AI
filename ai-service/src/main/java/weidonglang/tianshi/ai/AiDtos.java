package weidonglang.tianshi.ai;

import jakarta.validation.constraints.NotBlank;

import java.util.List;

public class AiDtos {
    public record RagAnswerRequest(@NotBlank String question, List<SourceDocument> documents) {
    }

    public record RagAnswerResponse(String answer, List<SourceDocument> sources, String serviceMode,
                                    String answerType, String refusalReason) {
    }

    public record SourceDocument(String id, String title, String type, String content, double score) {
    }

    public record SqlGenerateRequest(@NotBlank String question, List<TableSchema> schemas) {
    }

    public record SqlGenerateResponse(String sql, String explanation, List<String> warnings, String serviceMode) {
    }

    public record ChatRequest(@NotBlank String message, String modelName, Long modelId, Long sessionId, String thinkingMode) {
    }

    public record ChatResponse(String answer, String serviceMode, String modelName, boolean searchUsed,
                               List<Object> searchSources, String searchMessage, Long selectedModelId,
                               String selectedModelName, String actualModelName, boolean fallback,
                               String fallbackReason, String thinkingMode) {
    }

    public record StatusResponse(boolean ollamaEnabled, boolean ollamaReachable, String chatModel, String sqlModel, String lastError) {
    }

    public record LoadTestAnalysisRequest(Object report) {
    }

    public record LoadTestAnalysisResponse(String conclusion, List<String> bottlenecks, List<String> suggestions,
                                           String riskLevel, String serviceMode) {
    }

    public record TableSchema(String tableName, List<ColumnInfo> columns) {
    }

    public record ColumnInfo(String columnName, String dataType) {
    }
}
