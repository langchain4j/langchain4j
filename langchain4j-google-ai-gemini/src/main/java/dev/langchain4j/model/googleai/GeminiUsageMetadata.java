package dev.langchain4j.model.googleai;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
class GeminiUsageMetadata {
    private Integer promptTokenCount;
    private Integer cachedContentTokenCount;
    private Integer candidatesTokenCount;
    private Integer totalTokenCount;
}
