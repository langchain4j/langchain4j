package dev.langchain4j.model.googleai;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
class GeminiGenerateContentResponse {
    private List<GeminiCandidate> candidates;
    private GeminiPromptFeedback promptFeedback;
    private GeminiUsageMetadata usageMetadata;
}
