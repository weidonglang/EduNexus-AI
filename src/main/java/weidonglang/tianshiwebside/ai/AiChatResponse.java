package weidonglang.tianshiwebside.ai;

public record AiChatResponse(
        String answer,
        String serviceMode,
        String modelName,
        boolean searchUsed,
        java.util.List<AiSearchDtos.SearchResult> searchSources,
        String searchMessage,
        Long selectedModelId,
        String selectedModelName,
        String actualModelName,
        boolean fallback,
        String fallbackReason,
        String thinkingMode
) {
    public AiChatResponse(
            String answer,
            String serviceMode,
            String modelName,
            boolean searchUsed,
            java.util.List<AiSearchDtos.SearchResult> searchSources,
            String searchMessage
    ) {
        this(answer, serviceMode, modelName, searchUsed, searchSources, searchMessage, null, modelName, modelName, false, null, "AUTO");
    }
}
