package dev.langchain4j.model.gemini;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class GeminiAttributionSourceId {
    private GeminiGroundingPassageId groundingPassage;
    private GeminiSemanticRetrieverChunk semanticRetrieverChunk;
}
