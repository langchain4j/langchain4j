package dev.langchain4j.model.googleai;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class GeminiGroundingAttribution {
    private GeminiAttributionSourceId sourceId;
    private GeminiContent content;
}
