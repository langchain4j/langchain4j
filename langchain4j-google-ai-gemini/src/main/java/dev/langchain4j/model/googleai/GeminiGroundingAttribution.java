package dev.langchain4j.model.googleai;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
class GeminiGroundingAttribution {
    private GeminiAttributionSourceId sourceId;
    private GeminiContent content;
}
