package dev.langchain4j.model.mistralai;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
class MistralAiChatCompletionChoice {

    private Integer index;
    private MistralAiChatMessage message;
    private MistralAiDeltaMessage delta;
    private String finishReason;
    private MistralAiUsage usage; // usageInfo is returned only when the prompt is finished in stream mode
}
