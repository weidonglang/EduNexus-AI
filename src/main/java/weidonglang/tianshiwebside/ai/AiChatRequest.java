package weidonglang.tianshiwebside.ai;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AiChatRequest(
        @NotBlank
        @Size(max = 1000)
        String message,
        Long modelId
) {
}
