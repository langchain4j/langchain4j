package dev.langchain4j.model.googleai;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
class GeminiAttributionSourceId {
    private GeminiGroundingPassageId groundingPassage;
    private GeminiSemanticRetrieverChunk semanticRetrieverChunk;
}
