package dev.langchain4j.model.gemini;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class GeminiExecutableCode {
    private GeminiLanguage programmingLanguage = GeminiLanguage.PYTHON;
    private String code;
}
