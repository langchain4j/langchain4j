package dev.langchain4j.model.mistralai;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;


@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
class ChatCompletionChoice {

    private Integer index;
    private ChatMessage message;
    private Delta delta;
    private String finishReason;
    private UsageInfo usage; //usageInfo is returned only when the prompt is finished in stream mode
}
