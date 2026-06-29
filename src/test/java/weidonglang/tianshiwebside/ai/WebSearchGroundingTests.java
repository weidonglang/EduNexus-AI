package weidonglang.tianshiwebside.ai;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class WebSearchGroundingTests {
    private final AiChatService service = new AiChatService(null, null, null, null, null);

    @Test
    void groundedPromptContainsSearchResultsAndNoThinkingDirective() {
        AiSearchDtos.SearchTestResponse search = new AiSearchDtos.SearchTestResponse(
                true,
                true,
                "LOCAL_DEMO",
                "搜索完成",
                List.of(
                        new AiSearchDtos.SearchResult("第一条标题", "https://example.com/one", "第一条摘要", Instant.now()),
                        new AiSearchDtos.SearchResult("第二条标题", "https://example.com/two", "第二条摘要", Instant.now())
                ),
                12L
        );

        String prompt = service.groundedPrompt("只返回第 1 条标题和链接，不要解释", search);

        assertThat(prompt)
                .contains("你必须严格基于【联网搜索结果】回答")
                .contains("1. 标题：第一条标题")
                .contains("链接：https://example.com/one")
                .contains("【用户问题】")
                .contains("不要说“我无法联网搜索”")
                .doesNotContain("/think")
                .doesNotContain("/no_think");
    }

    @Test
    void groundedPromptLimitsResultsAndSummaryLength() {
        String longSummary = "摘要".repeat(300);
        AiSearchDtos.SearchTestResponse search = new AiSearchDtos.SearchTestResponse(
                true,
                true,
                "LOCAL_DEMO",
                "搜索完成",
                List.of(
                        result(1, longSummary),
                        result(2, longSummary),
                        result(3, longSummary),
                        result(4, longSummary),
                        result(5, longSummary),
                        result(6, longSummary)
                ),
                12L
        );

        String prompt = service.groundedPrompt("最新政策是什么", search);

        assertThat(prompt).contains("5. 标题：标题5").doesNotContain("6. 标题：标题6");
        assertThat(prompt.length()).isLessThan(3200);
    }

    private AiSearchDtos.SearchResult result(int index, String summary) {
        return new AiSearchDtos.SearchResult("标题" + index, "https://example.com/" + index, summary, Instant.now());
    }
}
