package weidonglang.tianshiwebside.ai;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.List;
import java.util.Map;

@FeignClient(name = "${app.ai-service.name:academic-ai-service}", contextId = "aiServiceFeignClient")
public interface AiServiceFeignClient {
    @PostMapping("/internal/ai/rag/answer")
    AiAssistantResponse ask(@RequestBody RagAnswerPayload payload);

    @PostMapping("/internal/ai/chat")
    AiChatResponse chat(@RequestBody ChatPayload payload);

    @PostMapping("/internal/ai/load-test/analyze")
    LoadTestAnalysisResponse analyzeLoadTest(@RequestBody Map<String, Object> payload);

    @GetMapping("/internal/ai/status")
    Map<String, Object> status();

    @PostMapping("/internal/ai/sql/generate")
    Map<String, Object> generateSql(@RequestBody SqlGeneratePayload payload);

    record RagAnswerPayload(String question, List<AiSourceDocument> documents) {
    }

    record ChatPayload(String message, String modelName, Long modelId, Long sessionId, String thinkingMode) {
    }

    record SqlGeneratePayload(String question, List<SqlSchemaService.TableSchema> schemas) {
    }
}
