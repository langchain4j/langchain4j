package dev.langchain4j.model.openai;

import static dev.langchain4j.internal.Utils.getOrDefault;

import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.request.DefaultChatRequestParameters;
import java.util.Objects;

public class OpenAiResponsesChatRequestParameters extends DefaultChatRequestParameters {

    public static final OpenAiResponsesChatRequestParameters EMPTY =
            OpenAiResponsesChatRequestParameters.builder().build();

    private final String previousResponseId;

    private OpenAiResponsesChatRequestParameters(Builder builder) {
        super(builder);
        this.previousResponseId = builder.previousResponseId;
    }

    public String previousResponseId() {
        return previousResponseId;
    }

    @Override
    public OpenAiResponsesChatRequestParameters overrideWith(ChatRequestParameters that) {
        return OpenAiResponsesChatRequestParameters.builder()
                .overrideWith(this)
                .overrideWith(that)
                .build();
    }

    @Override
    public OpenAiResponsesChatRequestParameters defaultedBy(ChatRequestParameters that) {
        return OpenAiResponsesChatRequestParameters.builder()
                .overrideWith(that)
                .overrideWith(this)
                .build();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;
        OpenAiResponsesChatRequestParameters that = (OpenAiResponsesChatRequestParameters) o;
        return Objects.equals(previousResponseId, that.previousResponseId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), previousResponseId);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder extends DefaultChatRequestParameters.Builder<Builder> {

        private String previousResponseId;

        @Override
        public Builder overrideWith(ChatRequestParameters parameters) {
            super.overrideWith(parameters);
            if (parameters instanceof OpenAiResponsesChatRequestParameters openAiResponsesParameters) {
                previousResponseId(getOrDefault(openAiResponsesParameters.previousResponseId(), previousResponseId));
            }
            return this;
        }

        public Builder previousResponseId(String previousResponseId) {
            this.previousResponseId = previousResponseId;
            return this;
        }

        @Override
        public OpenAiResponsesChatRequestParameters build() {
            return new OpenAiResponsesChatRequestParameters(this);
        }
    }
}
