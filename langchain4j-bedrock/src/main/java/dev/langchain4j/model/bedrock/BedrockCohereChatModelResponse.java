package dev.langchain4j.model.bedrock;

import dev.langchain4j.model.bedrock.internal.BedrockChatModelResponse;
import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.model.output.TokenUsage;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * @deprecated please use {@link BedrockChatModel}
 */
@Deprecated(forRemoval = true, since = "1.0.0-beta2")
@Getter
@Setter
public class BedrockCohereChatModelResponse implements BedrockChatModelResponse {

    @Getter
    @Setter
    public static class TokenLikelihood {
        private String token;
        private float likelihood;
    }

    @Getter
    @Setter
    public static class Generation {
        private String id;
        private String text;
        private String finish_reason;
        private List<TokenLikelihood> token_likelihoods;
    }

    private String id;
    private List<Generation> generations;
    private String prompt;


    @Override
    public String getOutputText() {
        return generations.get(0).text;
    }

    @Override
    public FinishReason getFinishReason() {
        final String finishReason = generations.get(0).finish_reason;
        if ("COMPLETE".equals(finishReason)) {
            return FinishReason.STOP;
        }

        throw new IllegalStateException("Unknown finish reason: " + finishReason);
    }

    @Override
    public TokenUsage getTokenUsage() {
        return null;
    }
}
