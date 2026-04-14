package dev.langchain4j.model.openaiofficial;

import dev.langchain4j.Experimental;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.request.DefaultChatRequestParameters;

import java.util.Objects;

import static dev.langchain4j.internal.Utils.getOrDefault;

@Experimental
public class OpenAiOfficialResponsesChatRequestParameters extends DefaultChatRequestParameters {

    public static final OpenAiOfficialResponsesChatRequestParameters EMPTY =
            OpenAiOfficialResponsesChatRequestParameters.builder().build();

    private final String previousResponseId;
    private final Boolean strictTools;
    private final Boolean strictJsonSchema;

    private OpenAiOfficialResponsesChatRequestParameters(Builder builder) {
        super(builder);
        this.previousResponseId = builder.previousResponseId;
        this.strictTools = builder.strictTools;
        this.strictJsonSchema = builder.strictJsonSchema;
    }

    public String previousResponseId() {
        return previousResponseId;
    }

    public Boolean strictTools() {
        return strictTools;
    }

    public Boolean strictJsonSchema() {
        return strictJsonSchema;
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
        return Objects.equals(previousResponseId, that.previousResponseId)
                && Objects.equals(strictTools, that.strictTools)
                && Objects.equals(strictJsonSchema, that.strictJsonSchema);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), previousResponseId, strictTools, strictJsonSchema);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder extends DefaultChatRequestParameters.Builder<Builder> {

        private String previousResponseId;
        private Boolean strictTools;
        private Boolean strictJsonSchema;

        @Override
        public Builder overrideWith(ChatRequestParameters parameters) {
            super.overrideWith(parameters);
            if (parameters instanceof OpenAiOfficialResponsesChatRequestParameters p) {
                previousResponseId(getOrDefault(p.previousResponseId(), previousResponseId));
                strictTools(getOrDefault(p.strictTools(), strictTools));
                strictJsonSchema(getOrDefault(p.strictJsonSchema(), strictJsonSchema));
            }
            return this;
        }

        public Builder previousResponseId(String previousResponseId) {
            this.previousResponseId = previousResponseId;
            return this;
        }

        public Builder strictTools(Boolean strictTools) {
            this.strictTools = strictTools;
            return this;
        }

        public Builder strictJsonSchema(Boolean strictJsonSchema) {
            this.strictJsonSchema = strictJsonSchema;
            return this;
        }

        @Override
        public OpenAiOfficialResponsesChatRequestParameters build() {
            return new OpenAiOfficialResponsesChatRequestParameters(this);
        }
    }
}
