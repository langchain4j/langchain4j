package dev.langchain4j.model.bedrock;

import dev.langchain4j.model.bedrock.internal.BedrockChatModelResponse;
import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.model.output.TokenUsage;
import java.util.List;

/**
 * @deprecated please use {@link BedrockChatModel}
 */
@Deprecated(forRemoval = true, since = "1.0.0-beta2")
class BedrockMistralAiChatModelResponse implements BedrockChatModelResponse {

    private List<Output> outputs;

    public static class Output {
        private String text;
        private String stop_reason;

        public String getText() {
            return text;
        }

        public void setText(final String text) {
            this.text = text;
        }

        public String getStop_reason() {
            return stop_reason;
        }

        public void setStop_reason(final String stop_reason) {
            this.stop_reason = stop_reason;
        }
    }

    @Override
    public String getOutputText() {
        return outputs.get(0).text;
    }

    @Override
    public FinishReason getFinishReason() {
        String stop_reason = outputs.get(0).stop_reason;
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
        return null;
    }

    public List<Output> getOutputs() {
        return outputs;
    }

    public void setOutputs(final List<Output> outputs) {
        this.outputs = outputs;
    }
}
