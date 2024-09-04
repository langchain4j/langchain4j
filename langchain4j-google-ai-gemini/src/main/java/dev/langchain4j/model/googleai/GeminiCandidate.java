package dev.langchain4j.model.googleai;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
class GeminiCandidate {
    private GeminiContent content;
    private GeminiFinishReason finishReason;
    private List<GeminiSafetySetting> safetySettings;
    private GeminiCitationMetadata citationMetadata;
    private Integer tokenCount; //TODO check why token count for candidate seems to be zero or absent
    private List<GeminiGroundingAttribution> groundingAttributions;
    private Integer index;
}
