package dev.langchain4j.model.googleai;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
class GeminiTool {
    private List<GeminiFunctionDeclaration> functionDeclarations;
    private GeminiCodeExecution codeExecution;
}
