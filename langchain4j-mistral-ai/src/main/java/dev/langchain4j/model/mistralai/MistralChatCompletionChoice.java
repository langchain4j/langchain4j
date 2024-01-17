package dev.langchain4j.model.mistralai;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;


@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
class MistralChatCompletionChoice {

    private Integer index;
    private MistralChatMessage message;
    private MistralDeltaMessage delta;
    private String finishReason;
    private MistralUsageInfo usage; //usageInfo is returned only when the prompt is finished in stream mode
}
