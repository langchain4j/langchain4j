package dev.langchain4j.model.bedrock;

import dev.langchain4j.model.bedrock.internal.BedrockChatModelResponse;
import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.model.output.TokenUsage;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * Bedrock Titan Chat response
 */
@Getter
@Setter
public class BedrockTitanChatModelResponse implements BedrockChatModelResponse {

    @Override
    public String getOutputText() {
        if (results.isEmpty()) {
            throw new IllegalStateException("No results returned");
        }

        return results.get(0).outputText;
    }

    @Override
    public FinishReason getFinishReason() {
        if (results.isEmpty()) {
            throw new IllegalStateException("No results returned");
        }

        final Result result = results.get(0);
        switch (result.completionReason) {
            case "FINISH":
                return FinishReason.STOP;
            default:
                return FinishReason.LENGTH;
        }
    }

    @Override
    public TokenUsage getTokenUsage() {
        if (results.isEmpty()) {
            throw new IllegalStateException("No results returned");
        }

        final Result result = results.get(0);
        return new TokenUsage(inputTextTokenCount, result.tokenCount);
    }

    @Getter
    @Setter
    public static class Result {
        private int tokenCount;
        private String outputText;
        private String completionReason;
    }

    private int inputTextTokenCount;
    private List<Result> results;
}
