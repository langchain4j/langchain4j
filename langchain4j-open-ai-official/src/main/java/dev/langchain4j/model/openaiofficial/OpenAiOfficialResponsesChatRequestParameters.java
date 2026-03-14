package dev.langchain4j.model.openaiofficial;

import static dev.langchain4j.internal.Utils.getOrDefault;

import dev.langchain4j.Experimental;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.request.DefaultChatRequestParameters;
import java.util.Objects;

@Experimental
public class OpenAiOfficialResponsesChatRequestParameters extends DefaultChatRequestParameters {

    public static final OpenAiOfficialResponsesChatRequestParameters EMPTY =
            OpenAiOfficialResponsesChatRequestParameters.builder().build();

    private final String previousResponseId;

    private OpenAiOfficialResponsesChatRequestParameters(Builder builder) {
        super(builder);
        this.previousResponseId = builder.previousResponseId;
    }

    public String previousResponseId() {
        return previousResponseId;
    }

    @Override
    public OpenAiOfficialResponsesChatRequestParameters overrideWith(ChatRequestParameters that) {
        return OpenAiOfficialResponsesChatRequestParameters.builder()
                .overrideWith(this)
                .overrideWith(that)
                .build();
    }

    @Override
    public OpenAiOfficialResponsesChatRequestParameters defaultedBy(ChatRequestParameters that) {
        return OpenAiOfficialResponsesChatRequestParameters.builder()
                .overrideWith(that)
                .overrideWith(this)
                .build();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;
        OpenAiOfficialResponsesChatRequestParameters that = (OpenAiOfficialResponsesChatRequestParameters) o;
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
            if (parameters instanceof OpenAiOfficialResponsesChatRequestParameters openAiResponsesParameters) {
                previousResponseId(getOrDefault(openAiResponsesParameters.previousResponseId(), previousResponseId));
            }
            return this;
        }

        public Builder previousResponseId(String previousResponseId) {
            this.previousResponseId = previousResponseId;
            return this;
        }

        @Override
        public OpenAiOfficialResponsesChatRequestParameters build() {
            return new OpenAiOfficialResponsesChatRequestParameters(this);
        }
    }
}
