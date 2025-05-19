package dev.langchain4j.model.bedrock;

import dev.langchain4j.model.bedrock.internal.BedrockChatModelResponse;
import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.model.output.TokenUsage;

/**
 * @deprecated please use {@link BedrockChatModel}
 */
@Deprecated(forRemoval = true, since = "1.0.0-beta2")
public class BedrockAnthropicCompletionChatModelResponse implements BedrockChatModelResponse {

    private String completion;
    private String stop_reason;

    @Override
    public String getOutputText() {
        return completion;
    }

    @Override
    public FinishReason getFinishReason() {
        switch (stop_reason) {
            case "stop_sequence":
                return FinishReason.STOP;
            case "max_tokens":
                return FinishReason.LENGTH;
            default:
                throw new IllegalArgumentException("Unknown stop reason: " + stop_reason);
        }
    }

    @Override
    public TokenUsage getTokenUsage() {
        return null;
    }

    public String getCompletion() {
        return completion;
    }

    public void setCompletion(final String completion) {
        this.completion = completion;
    }

    public String getStop_reason() {
        return stop_reason;
    }

    public void setStop_reason(final String stop_reason) {
        this.stop_reason = stop_reason;
    }
}
