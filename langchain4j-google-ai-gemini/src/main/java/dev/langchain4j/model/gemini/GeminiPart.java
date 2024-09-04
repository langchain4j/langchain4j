package dev.langchain4j.model.gemini;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class GeminiPart {
    private String text;
    private GeminiBlob inlineData;
    private GeminiFunctionCall functionCall;
    private GeminiFunctionResponse functionResponse;
    private GeminiFileData fileData;
    private GeminiExecutableCode executableCode;
    private GeminiCodeExecutionResult codeExecutionResult;
}
