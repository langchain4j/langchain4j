package dev.langchain4j.model.googleai;

import java.util.List;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
record GeminiTool(List<GeminiFunctionDeclaration> functionDeclarations, GeminiCodeExecution codeExecution) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    record GeminiCodeExecution() {}

}
