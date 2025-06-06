package dev.langchain4j.model.bedrock;

import dev.langchain4j.model.bedrock.internal.BedrockChatModelResponse;
import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.model.output.TokenUsage;
import java.util.List;
import java.util.stream.Collectors;

/**
 * @deprecated please use {@link BedrockChatModel}
 */
@Deprecated(forRemoval = true, since = "1.0.0-beta2")
public class BedrockAnthropicMessageChatModelResponse implements BedrockChatModelResponse {

    private String id;
    private String model;
    private String type;
    private String role;
    private List<BedrockAnthropicContent> content;
    private String stop_reason;
    private String stop_sequence;
    private BedrockAnthropicUsage usage;

    public static class BedrockAnthropicUsage {
        private int input_tokens;
        private int output_tokens;

        public int getInput_tokens() {
            return input_tokens;
        }

        public void setInput_tokens(final int input_tokens) {
            this.input_tokens = input_tokens;
        }

        public int getOutput_tokens() {
            return output_tokens;
        }

        public void setOutput_tokens(final int output_tokens) {
            this.output_tokens = output_tokens;
        }
    }

    @Override
    public String getOutputText() {
        return content.stream().map(BedrockAnthropicContent::getText).collect(Collectors.joining("\n\n"));
    }

    @Override
    public FinishReason getFinishReason() {
        switch (stop_reason) {
            case "end_turn":
            case "stop_sequence":
                return FinishReason.STOP;
            case "max_tokens":
                return FinishReason.LENGTH;
            case "tool_use":
                return FinishReason.TOOL_EXECUTION;
            default:
                throw new IllegalArgumentException("Unknown stop reason: " + stop_reason);
        }
    }

    @Override
    public TokenUsage getTokenUsage() {
        if (usage != null) {
            int totalTokenCount = usage.input_tokens + usage.output_tokens;
            return new TokenUsage(usage.input_tokens, usage.output_tokens, totalTokenCount);
        } else {
            return null;
        }
    }

    public String getId() {
        return id;
    }

    public void setId(final String id) {
        this.id = id;
    }

    public String getModel() {
        return model;
    }

    public void setModel(final String model) {
        this.model = model;
    }

    public String getType() {
        return type;
    }

    public void setType(final String type) {
        this.type = type;
    }

    public String getRole() {
        return role;
    }

    public void setRole(final String role) {
        this.role = role;
    }

    public List<BedrockAnthropicContent> getContent() {
        return content;
    }

    public void setContent(final List<BedrockAnthropicContent> content) {
        this.content = content;
    }

    public String getStop_reason() {
        return stop_reason;
    }

    public void setStop_reason(final String stop_reason) {
        this.stop_reason = stop_reason;
    }

    public String getStop_sequence() {
        return stop_sequence;
    }

    public void setStop_sequence(final String stop_sequence) {
        this.stop_sequence = stop_sequence;
    }

    public BedrockAnthropicUsage getUsage() {
        return usage;
    }

    public void setUsage(final BedrockAnthropicUsage usage) {
        this.usage = usage;
    }
}
