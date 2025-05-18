package dev.langchain4j.model.bedrock;

import dev.langchain4j.model.bedrock.internal.BedrockChatModelResponse;
import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.model.output.TokenUsage;

/**
 * @deprecated please use {@link BedrockChatModel}
 */
@Deprecated(forRemoval = true, since = "1.0.0-beta2")
public class BedrockLlamaChatModelResponse implements BedrockChatModelResponse {

    private String generation;
    private int prompt_token_count;
    private int generation_token_count;
    private String stop_reason;

    @Override
    public String getOutputText() {
        return generation;
    }

    @Override
    public FinishReason getFinishReason() {
        switch (stop_reason) {
            case "stop":
                return FinishReason.STOP;
            case "length":
                return FinishReason.LENGTH;
            default:
                throw new IllegalArgumentException("Unknown stop reason: " + stop_reason);
        }
    }

    @Override
    public TokenUsage getTokenUsage() {
        return new TokenUsage(prompt_token_count, generation_token_count);
    }

    public String getGeneration() {
        return generation;
    }

    public void setGeneration(final String generation) {
        this.generation = generation;
    }

    public int getPrompt_token_count() {
        return prompt_token_count;
    }

    public void setPrompt_token_count(final int prompt_token_count) {
        this.prompt_token_count = prompt_token_count;
    }

    public int getGeneration_token_count() {
        return generation_token_count;
    }

    public void setGeneration_token_count(final int generation_token_count) {
        this.generation_token_count = generation_token_count;
    }

    public String getStop_reason() {
        return stop_reason;
    }

    public void setStop_reason(final String stop_reason) {
        this.stop_reason = stop_reason;
    }
}
