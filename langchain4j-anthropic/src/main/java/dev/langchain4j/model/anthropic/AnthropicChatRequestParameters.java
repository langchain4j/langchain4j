package dev.langchain4j.model.anthropic;

import static dev.langchain4j.internal.Utils.getOrDefault;

import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.request.DefaultChatRequestParameters;
import java.util.Objects;

/**
 * Anthropic-specific {@link ChatRequestParameters} that can be set per request (i.e., per {@code ChatRequest}),
 * overriding the values configured on the {@link AnthropicChatModel} / {@link AnthropicStreamingChatModel} builder.
 * <p>
 * Currently supports controlling prompt caching of {@link dev.langchain4j.data.message.SystemMessage}s and
 * {@link dev.langchain4j.agent.tool.ToolSpecification}s on a per-request basis.
 */
public class AnthropicChatRequestParameters extends DefaultChatRequestParameters {

    public static final AnthropicChatRequestParameters EMPTY =
            AnthropicChatRequestParameters.builder().build();

    private final Boolean cacheSystemMessages;
    private final Boolean cacheTools;

    private AnthropicChatRequestParameters(Builder builder) {
        super(builder);
        this.cacheSystemMessages = builder.cacheSystemMessages;
        this.cacheTools = builder.cacheTools;
    }

    public Boolean cacheSystemMessages() {
        return cacheSystemMessages;
    }

    public Boolean cacheTools() {
        return cacheTools;
    }

    @Override
    public AnthropicChatRequestParameters overrideWith(ChatRequestParameters that) {
        return AnthropicChatRequestParameters.builder()
                .overrideWith(this)
                .overrideWith(that)
                .build();
    }

    @Override
    public AnthropicChatRequestParameters defaultedBy(ChatRequestParameters that) {
        return AnthropicChatRequestParameters.builder()
                .overrideWith(that)
                .overrideWith(this)
                .build();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;
        AnthropicChatRequestParameters that = (AnthropicChatRequestParameters) o;
        return Objects.equals(cacheSystemMessages, that.cacheSystemMessages)
                && Objects.equals(cacheTools, that.cacheTools);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), cacheSystemMessages, cacheTools);
    }

    @Override
    public String toString() {
        return "AnthropicChatRequestParameters{" + "modelName="
                + modelName() + ", temperature="
                + temperature() + ", topP="
                + topP() + ", topK="
                + topK() + ", frequencyPenalty="
                + frequencyPenalty() + ", presencePenalty="
                + presencePenalty() + ", maxOutputTokens="
                + maxOutputTokens() + ", stopSequences="
                + stopSequences() + ", toolSpecifications="
                + toolSpecifications() + ", toolChoice="
                + toolChoice() + ", responseFormat="
                + responseFormat() + ", cacheSystemMessages="
                + cacheSystemMessages + ", cacheTools="
                + cacheTools + '}';
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder extends DefaultChatRequestParameters.Builder<Builder> {

        private Boolean cacheSystemMessages;
        private Boolean cacheTools;

        @Override
        public Builder overrideWith(ChatRequestParameters parameters) {
            super.overrideWith(parameters);
            if (parameters instanceof AnthropicChatRequestParameters anthropicParameters) {
                cacheSystemMessages(getOrDefault(anthropicParameters.cacheSystemMessages(), cacheSystemMessages));
                cacheTools(getOrDefault(anthropicParameters.cacheTools(), cacheTools));
            }
            return this;
        }

        public Builder modelName(AnthropicChatModelName modelName) {
            return super.modelName(modelName == null ? null : modelName.toString());
        }

        /**
         * Controls whether to cache {@link dev.langchain4j.data.message.SystemMessage}s for this request.
         *
         * @see AnthropicChatModel.AnthropicChatModelBuilder#cacheSystemMessages(Boolean)
         */
        public Builder cacheSystemMessages(Boolean cacheSystemMessages) {
            this.cacheSystemMessages = cacheSystemMessages;
            return this;
        }

        /**
         * Controls whether to cache {@link dev.langchain4j.agent.tool.ToolSpecification}s for this request.
         *
         * @see AnthropicChatModel.AnthropicChatModelBuilder#cacheTools(Boolean)
         */
        public Builder cacheTools(Boolean cacheTools) {
            this.cacheTools = cacheTools;
            return this;
        }

        @Override
        public AnthropicChatRequestParameters build() {
            return new AnthropicChatRequestParameters(this);
        }
    }
}
