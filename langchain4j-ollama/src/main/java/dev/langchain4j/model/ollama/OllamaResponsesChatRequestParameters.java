package dev.langchain4j.model.ollama;

import static dev.langchain4j.internal.Utils.getOrDefault;

import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.request.DefaultChatRequestParameters;
import java.util.Objects;

public class OllamaResponsesChatRequestParameters extends DefaultChatRequestParameters {

    public static final OllamaResponsesChatRequestParameters EMPTY =
            OllamaResponsesChatRequestParameters.builder().build();

    private final String instructions;

    private OllamaResponsesChatRequestParameters(Builder builder) {
        super(builder);
        this.instructions = builder.instructions;
    }

    public String instructions() {
        return instructions;
    }

    @Override
    public OllamaResponsesChatRequestParameters overrideWith(ChatRequestParameters that) {
        return OllamaResponsesChatRequestParameters.builder()
                .overrideWith(this)
                .overrideWith(that)
                .build();
    }

    @Override
    public OllamaResponsesChatRequestParameters defaultedBy(ChatRequestParameters that) {
        return OllamaResponsesChatRequestParameters.builder()
                .overrideWith(that)
                .overrideWith(this)
                .build();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;
        OllamaResponsesChatRequestParameters that = (OllamaResponsesChatRequestParameters) o;
        return Objects.equals(instructions, that.instructions);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), instructions);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder extends DefaultChatRequestParameters.Builder<Builder> {

        private String instructions;

        @Override
        public Builder overrideWith(ChatRequestParameters parameters) {
            super.overrideWith(parameters);
            if (parameters instanceof OllamaResponsesChatRequestParameters p) {
                instructions(getOrDefault(p.instructions(), instructions));
            }
            return this;
        }

        public Builder instructions(String instructions) {
            this.instructions = instructions;
            return this;
        }

        @Override
        public OllamaResponsesChatRequestParameters build() {
            return new OllamaResponsesChatRequestParameters(this);
        }
    }
}
