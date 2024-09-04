package dev.langchain4j.model.googleai;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
class GeminiExecutableCode {
    private GeminiLanguage programmingLanguage = GeminiLanguage.PYTHON;
    private String code;
}
