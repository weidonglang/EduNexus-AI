package weidonglang.tianshiwebside.ai;

import java.util.List;

public record AiAssistantResponse(
        String answer,
        List<AiSourceDocument> sources,
        String serviceMode,
        String answerType,
        String refusalReason
) {
}
