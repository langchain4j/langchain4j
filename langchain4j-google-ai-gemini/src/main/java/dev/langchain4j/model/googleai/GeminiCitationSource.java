package dev.langchain4j.model.googleai;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
class GeminiCitationSource {
    private Integer startIndex;
    private Integer endIndex;
    private String uri;
    private String license;
}
