package weidonglang.tianshi.ai;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AiThinkingModeTests {
    private final AiInternalController controller = new AiInternalController(
            new OllamaClient("http://localhost:11434", false, 180),
            "qwen3:8b",
            "qwen2.5-coder:7b"
    );

    @Test
    void autoDoesNotInjectThinkingDirective() {
        assertThat(controller.applyThinkingMode("你好", "AUTO", "qwen3:8b"))
                .isEqualTo("你好");
    }

    @Test
    void onAndOffInjectDirectivesForSupportedModels() {
        assertThat(controller.applyThinkingMode("你好", "ON", "qwen3:8b"))
                .startsWith("/think\n");
        assertThat(controller.applyThinkingMode("你好", "OFF", "qwen3:8b"))
                .startsWith("/no_think\n");
    }

    @Test
    void explicitDirectiveIsNotDuplicatedAndUnsupportedModelKeepsMessage() {
        assertThat(controller.applyThinkingMode("/think\n你好", "ON", "qwen3:8b"))
                .isEqualTo("/think\n你好");
        assertThat(controller.applyThinkingMode("/no_think\n你好", "OFF", "qwen3:8b"))
                .isEqualTo("/no_think\n你好");
        assertThat(controller.applyThinkingMode("你好", "ON", "plain-chat-model"))
                .isEqualTo("你好");
    }
}
