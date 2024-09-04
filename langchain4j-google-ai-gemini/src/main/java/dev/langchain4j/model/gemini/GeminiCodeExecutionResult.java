package dev.langchain4j.model.gemini;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class GeminiCodeExecutionResult {
    private GeminiOutcome outcome; //TODO how to deal with the non-OK outcomes?
    private String output;
}
