package weidonglang.tianshiwebside.ai;

import org.springframework.stereotype.Service;
import weidonglang.tianshiwebside.governance.ContentModerationService;

import java.security.Principal;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class AiChatService {
    private static final Pattern URL_PATTERN = Pattern.compile("https?://\\S+");

    private final AiRemoteClient remoteClient;
    private final AiCallLogService callLogService;
    private final AiModelRegistryService modelRegistryService;
    private final AiSearchService searchService;
    private final ContentModerationService moderationService;

    public AiChatService(
            AiRemoteClient remoteClient,
            AiCallLogService callLogService,
            AiModelRegistryService modelRegistryService,
            AiSearchService searchService,
            ContentModerationService moderationService
    ) {
        this.remoteClient = remoteClient;
        this.callLogService = callLogService;
        this.modelRegistryService = modelRegistryService;
        this.searchService = searchService;
        this.moderationService = moderationService;
    }

    public AiChatResponse chat(String message, Principal principal) {
        return chat(message, principal, null, null, "AUTO");
    }

    public AiChatResponse chat(String message, Principal principal, Long modelId, Long sessionId) {
        return chat(message, principal, modelId, sessionId, "AUTO");
    }

    public AiChatResponse chat(String message, Principal principal, Long modelId, Long sessionId, String thinkingMode) {
        long start = System.nanoTime();
        String normalizedThinkingMode = normalizeThinkingMode(thinkingMode);
        moderationService.checkConfigured("AI_INPUT", message, operator(principal));
        AiModelRecord selectedModel = modelRegistryService.requireEnabledChatModel(modelId);
        AiSearchDtos.SearchTestResponse search = maybeSearch(message, principal);

        if (shouldReturnFirstSearchResultOnly(message, search)) {
            String answer = appendSearchNotice(firstSearchResultAnswer(search), search);
            moderationService.checkConfigured("AI_OUTPUT", answer, operator(principal));
            callLogService.record(principal, "CHAT", message, selectedModel.modelName(), "search-direct",
                    elapsedMillis(start), true, search.message(), sessionId, selectedModel.id(),
                    selectedModel.modelName(), selectedModel.modelName(), null);
            return new AiChatResponse(answer, "search-direct", selectedModel.modelName(), true,
                    search.results(), search.message(), selectedModel.id(), selectedModel.modelName(),
                    selectedModel.modelName(), false, null, normalizedThinkingMode);
        }

        String prompt = search.searchUsed() && !search.results().isEmpty()
                ? groundedPrompt(message, search)
                : message;
        return remoteClient.chat(prompt, selectedModel.id(), selectedModel.modelName(), sessionId, normalizedThinkingMode)
                .map(response -> {
                    String actualModelName = response.actualModelName() == null || response.actualModelName().isBlank()
                            ? response.modelName()
                            : response.actualModelName();
                    String modelName = actualModelName == null || actualModelName.isBlank()
                            ? selectedModel.modelName()
                            : actualModelName;
                    String answer = appendSearchNotice(sanitizeUnreferencedUrls(response.answer(), search), search);
                    moderationService.checkConfigured("AI_OUTPUT", answer, operator(principal));
                    String warning = firstNonBlank(response.fallbackReason(), search.message());
                    callLogService.record(principal, "CHAT", message, modelName, response.serviceMode(),
                            elapsedMillis(start), true, warning, sessionId, selectedModel.id(),
                            selectedModel.modelName(), modelName, response.fallbackReason());
                    return new AiChatResponse(
                            answer,
                            response.serviceMode(),
                            modelName,
                            search.searchUsed(),
                            search.results(),
                            search.message(),
                            selectedModel.id(),
                            selectedModel.modelName(),
                            modelName,
                            response.fallback(),
                            response.fallbackReason(),
                            response.thinkingMode() == null ? normalizedThinkingMode : response.thinkingMode()
                    );
                })
                .orElseGet(() -> {
                    String answer = "AI 聊天服务暂不可用，当前为本地兜底模式。这个聊天入口不作为教务依据；涉及教务规则请使用智能教务助手。";
                    String moderatedAnswer = appendSearchNotice(answer, search);
                    moderationService.checkConfigured("AI_OUTPUT", moderatedAnswer, operator(principal));
                    String fallbackReason = "ai-service unavailable";
                    callLogService.record(principal, "CHAT_FALLBACK", message, selectedModel.modelName(), "local-fallback",
                            elapsedMillis(start), true, fallbackReason + "; " + search.message(), sessionId, selectedModel.id(),
                            selectedModel.modelName(), selectedModel.modelName(), fallbackReason);
                    return new AiChatResponse(moderatedAnswer, "local-fallback", selectedModel.modelName(),
                            search.searchUsed(), search.results(), search.message(), selectedModel.id(),
                            selectedModel.modelName(), selectedModel.modelName(), true, fallbackReason, normalizedThinkingMode);
                });
    }

    private AiSearchDtos.SearchTestResponse maybeSearch(String message, Principal principal) {
        if (!searchService.shouldSearch(message)) {
            return new AiSearchDtos.SearchTestResponse(true, false, "", "未触发联网搜索", java.util.List.of(), null);
        }
        return searchService.search(principal == null ? "anonymous" : principal.getName(), "CHAT", message);
    }

    private String appendSearchNotice(String answer, AiSearchDtos.SearchTestResponse search) {
        if (!search.searchUsed() || search.results().isEmpty()) {
            return answer;
        }
        StringBuilder builder = new StringBuilder(answer).append("\n\n联网搜索参考：");
        for (int i = 0; i < Math.min(3, search.results().size()); i++) {
            AiSearchDtos.SearchResult result = search.results().get(i);
            builder.append("\n").append(i + 1).append(". ").append(result.title()).append(" - ").append(result.link());
        }
        return builder.toString();
    }

    String groundedPrompt(String message, AiSearchDtos.SearchTestResponse search) {
        StringBuilder results = new StringBuilder();
        int limit = Math.min(5, search.results().size());
        for (int i = 0; i < limit; i++) {
            AiSearchDtos.SearchResult result = search.results().get(i);
            results.append(i + 1)
                    .append(". 标题：")
                    .append(truncate(result.title(), 120))
                    .append("\n   链接：")
                    .append(result.link())
                    .append("\n   摘要：")
                    .append(truncate(result.summary(), 360))
                    .append("\n");
        }
        return """
                你是 Academic-Nexus 的联网搜索增强助手。
                系统已经替你完成联网搜索。
                你必须严格基于【联网搜索结果】回答。
                不要说“我无法联网搜索”。
                不要编造搜索结果之外的标题、链接或来源。
                如果用户要求返回第 1 条结果，就直接返回【联网搜索结果】里的第 1 条。
                如果搜索结果和你的内置知识冲突，以搜索结果为准。

                【联网搜索结果】
                %s
                【用户问题】
                %s
                """.formatted(results, message);
    }

    private boolean shouldReturnFirstSearchResultOnly(String message, AiSearchDtos.SearchTestResponse search) {
        if (!search.searchUsed() || search.results().isEmpty()) {
            return false;
        }
        String normalized = message == null ? "" : message.toLowerCase(Locale.ROOT);
        return (normalized.contains("第 1 条") || normalized.contains("第一条") || normalized.contains("first"))
                && (normalized.contains("标题") || normalized.contains("title"))
                && (normalized.contains("链接") || normalized.contains("url") || normalized.contains("link"))
                && (normalized.contains("只返回") || normalized.contains("不要解释") || normalized.contains("only"));
    }

    private String firstSearchResultAnswer(AiSearchDtos.SearchTestResponse search) {
        AiSearchDtos.SearchResult first = search.results().get(0);
        return first.title() + "\n" + first.link();
    }

    private String sanitizeUnreferencedUrls(String answer, AiSearchDtos.SearchTestResponse search) {
        if (!search.searchUsed() || search.results().isEmpty() || answer == null || answer.isBlank()) {
            return answer;
        }
        Set<String> allowed = search.results().stream()
                .map(AiSearchDtos.SearchResult::link)
                .collect(Collectors.toSet());
        Matcher matcher = URL_PATTERN.matcher(answer);
        StringBuffer cleaned = new StringBuffer();
        while (matcher.find()) {
            String url = matcher.group();
            String normalized = url.replaceAll("[，。；;,.）)]$", "");
            if (allowed.contains(normalized)) {
                matcher.appendReplacement(cleaned, Matcher.quoteReplacement(url));
            } else {
                matcher.appendReplacement(cleaned, "[已移除未引用链接]");
            }
        }
        matcher.appendTail(cleaned);
        return cleaned.toString();
    }

    private String normalizeThinkingMode(String mode) {
        String value = mode == null ? "AUTO" : mode.trim().toUpperCase(Locale.ROOT);
        return switch (value) {
            case "ON", "OFF" -> value;
            default -> "AUTO";
        };
    }

    private String truncate(String value, int maxLength) {
        if (value == null) {
            return "";
        }
        String normalized = value.replaceAll("\\s+", " ").trim();
        return normalized.length() <= maxLength ? normalized : normalized.substring(0, maxLength);
    }

    private long elapsedMillis(long startNanos) {
        return java.time.Duration.ofNanos(System.nanoTime() - startNanos).toMillis();
    }

    private String operator(Principal principal) {
        return principal == null ? "anonymous" : principal.getName();
    }

    private String firstNonBlank(String first, String second) {
        return first != null && !first.isBlank() ? first : second;
    }
}
