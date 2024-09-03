package dev.langchain4j.model.gemini;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class GeminiCitationSource {
    private Integer startIndex;
    private Integer endIndex;
    private String uri;
    private String license;
}
