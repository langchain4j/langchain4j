package dev.langchain4j.model.googleai;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
class GeminiCodeExecutionResult {
    private GeminiOutcome outcome; //TODO how to deal with the non-OK outcomes?
    private String output;
}
