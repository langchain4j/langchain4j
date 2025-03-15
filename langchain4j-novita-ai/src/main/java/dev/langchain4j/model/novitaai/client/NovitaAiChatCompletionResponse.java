package dev.langchain4j.model.novitaai.client;

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
@JsonDeserialize(builder = NovitaAiChatCompletionResponse.NovitaAiChatCompletionResponseBuilder.class)
public class NovitaAiChatCompletionResponse {

    private String id;
    private String object;
    private Integer created;
    private String model;
    private List<NovitaAiChatCompletionChoice> choices;
    private NovitaAiUsage usage;

    private NovitaAiChatCompletionResponse(NovitaAiChatCompletionResponseBuilder builder) {
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

    public List<NovitaAiChatCompletionChoice> getChoices() {
        return this.choices;
    }

    public NovitaAiUsage getUsage() {
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
        final NovitaAiChatCompletionResponse other = (NovitaAiChatCompletionResponse) obj;
        return Objects.equals(this.id, other.id)
                && Objects.equals(this.object, other.object)
                && Objects.equals(this.model, other.model)
                && Objects.equals(this.created, other.created)
                && Objects.equals(this.choices, other.choices)
                && Objects.equals(this.usage, other.usage);
    }

    @Override
    public String toString() {
        return new StringJoiner(", ", "NovitaAiChatCompletionResponse [", "]")
                .add("id=" + this.getId())
                .add(", object=" + this.getObject())
                .add(", created=" + this.getCreated())
                .add(", model=" + this.getModel())
                .add(", choices=" + this.getChoices())
                .add(", usage=" + this.getUsage())
                .toString();
    }

    public static NovitaAiChatCompletionResponseBuilder builder() {
        return new NovitaAiChatCompletionResponseBuilder();
    }

    @JsonPOJOBuilder(withPrefix = "")
    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonNaming(SnakeCaseStrategy.class)
    public static class NovitaAiChatCompletionResponseBuilder {

        private String id;
        private String object;
        private Integer created;
        private String model;
        private List<NovitaAiChatCompletionChoice> choices;
        private NovitaAiUsage usage;

        private NovitaAiChatCompletionResponseBuilder() {}

        public NovitaAiChatCompletionResponseBuilder id(String id) {
            this.id = id;
            return this;
        }

        public NovitaAiChatCompletionResponseBuilder object(String object) {
            this.object = object;
            return this;
        }

        public NovitaAiChatCompletionResponseBuilder created(Integer created) {
            this.created = created;
            return this;
        }

        public NovitaAiChatCompletionResponseBuilder model(String model) {
            this.model = model;
            return this;
        }

        public NovitaAiChatCompletionResponseBuilder choices(List<NovitaAiChatCompletionChoice> choices) {
            this.choices = choices;
            return this;
        }

        public NovitaAiChatCompletionResponseBuilder usage(NovitaAiUsage usage) {
            this.usage = usage;
            return this;
        }

        public NovitaAiChatCompletionResponse build() {
            return new NovitaAiChatCompletionResponse(this);
        }
    }
}
