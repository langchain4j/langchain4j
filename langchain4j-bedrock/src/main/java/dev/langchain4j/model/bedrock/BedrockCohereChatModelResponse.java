package dev.langchain4j.model.bedrock;

import dev.langchain4j.model.bedrock.internal.BedrockChatModelResponse;
import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.model.output.TokenUsage;
import java.util.List;

/**
 * @deprecated please use {@link BedrockChatModel}
 */
@Deprecated(forRemoval = true, since = "1.0.0-beta2")
public class BedrockCohereChatModelResponse implements BedrockChatModelResponse {

    public static class TokenLikelihood {
        private String token;
        private float likelihood;

        public String getToken() {
            return token;
        }

        public void setToken(final String token) {
            this.token = token;
        }

        public float getLikelihood() {
            return likelihood;
        }

        public void setLikelihood(final float likelihood) {
            this.likelihood = likelihood;
        }
    }

    public static class Generation {
        private String id;
        private String text;
        private String finish_reason;
        private List<TokenLikelihood> token_likelihoods;

        public String getId() {
            return id;
        }

        public void setId(final String id) {
            this.id = id;
        }

        public String getText() {
            return text;
        }

        public void setText(final String text) {
            this.text = text;
        }

        public String getFinish_reason() {
            return finish_reason;
        }

        public void setFinish_reason(final String finish_reason) {
            this.finish_reason = finish_reason;
        }

        public List<TokenLikelihood> getToken_likelihoods() {
            return token_likelihoods;
        }

        public void setToken_likelihoods(final List<TokenLikelihood> token_likelihoods) {
            this.token_likelihoods = token_likelihoods;
        }
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

    public String getId() {
        return id;
    }

    public void setId(final String id) {
        this.id = id;
    }

    public List<Generation> getGenerations() {
        return generations;
    }

    public void setGenerations(final List<Generation> generations) {
        this.generations = generations;
    }

    public String getPrompt() {
        return prompt;
    }

    public void setPrompt(final String prompt) {
        this.prompt = prompt;
    }
}
