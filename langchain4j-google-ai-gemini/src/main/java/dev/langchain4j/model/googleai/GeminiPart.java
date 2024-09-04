package dev.langchain4j.model.googleai;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
class GeminiPart {
    private String text;
    private GeminiBlob inlineData;
    private GeminiFunctionCall functionCall;
    private GeminiFunctionResponse functionResponse;
    private GeminiFileData fileData;
    private GeminiExecutableCode executableCode;
    private GeminiCodeExecutionResult codeExecutionResult;
}
