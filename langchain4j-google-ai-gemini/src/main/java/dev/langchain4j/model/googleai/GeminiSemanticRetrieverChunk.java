package dev.langchain4j.model.googleai;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class GeminiSemanticRetrieverChunk {
    private String source;
    private String chunk;
}
