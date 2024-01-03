package dev.langchain4j.model.vertexai;

import com.google.cloud.vertexai.api.GenerateContentResponse;
import dev.langchain4j.model.output.TokenUsage;

class TokenUsageMapper {

    static TokenUsage map(GenerateContentResponse.UsageMetadata usageMetadata) {
        return new TokenUsage(
                usageMetadata.getPromptTokenCount(),
                usageMetadata.getCandidatesTokenCount(),
                usageMetadata.getTotalTokenCount()
        );
    }
}
