package dev.langchain4j.model.mistralai;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
class MistralAiChatCompletionRequest {

    private String model;
    private List<MistralAiChatMessage> messages;
    private Double temperature;
    private Double topP;
    private Integer maxTokens;
    private Boolean stream;
    private Boolean safePrompt;
    private Integer randomSeed;
}
