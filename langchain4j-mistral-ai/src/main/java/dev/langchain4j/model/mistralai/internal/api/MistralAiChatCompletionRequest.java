package dev.langchain4j.model.mistralai.internal.api;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
@JsonInclude(NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonNaming(SnakeCaseStrategy.class)
public class MistralAiChatCompletionRequest {

    private String model;
    private List<MistralAiChatMessage> messages;
    private Double temperature;
    private Double topP;
    private Integer maxTokens;
    private Boolean stream;
    private Boolean safePrompt;
    private Integer randomSeed;
    private List<MistralAiTool> tools;
    private MistralAiToolChoiceName toolChoice;
    private MistralAiResponseFormat responseFormat;
}
