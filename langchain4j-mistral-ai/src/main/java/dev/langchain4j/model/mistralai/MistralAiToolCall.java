package dev.langchain4j.model.mistralai;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
class MistralAiToolCall {

    private String id;
    @Builder.Default
    private MistralAiToolType type = MistralAiToolType.FUNCTION;
    private MistralAiFunctionCall function;
}
