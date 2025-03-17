package dev.langchain4j.model.mistralai.internal.api;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import java.util.List;
import java.util.Objects;
import java.util.StringJoiner;

@JsonInclude(NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonNaming(SnakeCaseStrategy.class)
@JsonDeserialize(builder = MistralAiChatCompletionResponse.MistralAiChatCompletionResponseBuilder.class)
public class MistralAiChatCompletionResponse {

    private String id;
    private String object;
    private Integer created;
    private String model;
    private List<MistralAiChatCompletionChoice> choices;
    private MistralAiUsage usage;

    private MistralAiChatCompletionResponse(MistralAiChatCompletionResponseBuilder builder) {
        this.id = builder.id;
        this.object = builder.object;
        this.created = builder.created;
        this.model = builder.model;
        this.choices = builder.choices;
        this.usage = builder.usage;
    }

    public String getId() {
        return this.id;
    }

    public String getObject() {
        return this.object;
    }

    public Integer getCreated() {
        return this.created;
    }

    public String getModel() {
        return this.model;
    }

    public List<MistralAiChatCompletionChoice> getChoices() {
        return this.choices;
    }

    public MistralAiUsage getUsage() {
        return this.usage;
    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 97 * hash + Objects.hashCode(this.id);
        hash = 97 * hash + Objects.hashCode(this.object);
        hash = 97 * hash + Objects.hashCode(this.created);
        hash = 97 * hash + Objects.hashCode(this.model);
        hash = 97 * hash + Objects.hashCode(this.choices);
        hash = 97 * hash + Objects.hashCode(this.usage);
        return hash;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }
        final MistralAiChatCompletionResponse other = (MistralAiChatCompletionResponse) obj;
        return Objects.equals(this.id, other.id)
                && Objects.equals(this.object, other.object)
                && Objects.equals(this.model, other.model)
                && Objects.equals(this.created, other.created)
                && Objects.equals(this.choices, other.choices)
                && Objects.equals(this.usage, other.usage);
    }

    @Override
    public String toString() {
        return new StringJoiner(", ", "MistralAiChatCompletionResponse [", "]")
                .add("id=" + this.getId())
                .add(", object=" + this.getObject())
                .add(", created=" + this.getCreated())
                .add(", model=" + this.getModel())
                .add(", choices=" + this.getChoices())
                .add(", usage=" + this.getUsage())
                .toString();
    }

    public static MistralAiChatCompletionResponseBuilder builder() {
        return new MistralAiChatCompletionResponseBuilder();
    }

    @JsonPOJOBuilder(withPrefix = "")
    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonNaming(SnakeCaseStrategy.class)
    public static class MistralAiChatCompletionResponseBuilder {

        private String id;
        private String object;
        private Integer created;
        private String model;
        private List<MistralAiChatCompletionChoice> choices;
        private MistralAiUsage usage;

        private MistralAiChatCompletionResponseBuilder() {}

        public MistralAiChatCompletionResponseBuilder id(String id) {
            this.id = id;
            return this;
        }

        public MistralAiChatCompletionResponseBuilder object(String object) {
            this.object = object;
            return this;
        }

        public MistralAiChatCompletionResponseBuilder created(Integer created) {
            this.created = created;
            return this;
        }

        public MistralAiChatCompletionResponseBuilder model(String model) {
            this.model = model;
            return this;
        }

        public MistralAiChatCompletionResponseBuilder choices(List<MistralAiChatCompletionChoice> choices) {
            this.choices = choices;
            return this;
        }

        public MistralAiChatCompletionResponseBuilder usage(MistralAiUsage usage) {
            this.usage = usage;
            return this;
        }

        public MistralAiChatCompletionResponse build() {
            return new MistralAiChatCompletionResponse(this);
        }
    }
}
