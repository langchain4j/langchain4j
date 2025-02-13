package dev.langchain4j.model.openai.internal.completion;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import dev.langchain4j.model.openai.internal.shared.Usage;

import java.util.List;
import java.util.Objects;

import static java.util.Collections.unmodifiableList;

@JsonDeserialize(builder = CompletionResponse.Builder.class)
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public final class CompletionResponse {

    @JsonProperty
    private final String id;
    @JsonProperty
    private final Integer created;
    @JsonProperty
    private final String model;
    @JsonProperty
    private final List<CompletionChoice> choices;
    @JsonProperty
    private final Usage usage;

    public CompletionResponse(Builder builder) {
        this.id = builder.id;
        this.created = builder.created;
        this.model = builder.model;
        this.choices = builder.choices;
        this.usage = builder.usage;
    }

    public String id() {
        return id;
    }

    public Integer created() {
        return created;
    }

    public String model() {
        return model;
    }

    public List<CompletionChoice> choices() {
        return choices;
    }

    public Usage usage() {
        return usage;
    }

    /**
     * Convenience method to get the text from the first choice.
     */
    public String text() {
        return choices().get(0).text();
    }

    @Override
    public boolean equals(Object another) {
        if (this == another) return true;
        return another instanceof CompletionResponse
                && equalTo((CompletionResponse) another);
    }

    private boolean equalTo(CompletionResponse another) {
        return Objects.equals(id, another.id)
                && Objects.equals(created, another.created)
                && Objects.equals(model, another.model)
                && Objects.equals(choices, another.choices)
                && Objects.equals(usage, another.usage);
    }

    @Override
    public int hashCode() {
        int h = 5381;
        h += (h << 5) + Objects.hashCode(id);
        h += (h << 5) + Objects.hashCode(created);
        h += (h << 5) + Objects.hashCode(model);
        h += (h << 5) + Objects.hashCode(choices);
        h += (h << 5) + Objects.hashCode(usage);
        return h;
    }

    @Override
    public String toString() {
        return "CompletionResponse{"
                + "id=" + id
                + ", created=" + created
                + ", model=" + model
                + ", choices=" + choices
                + ", usage=" + usage
                + "}";
    }

    public static Builder builder() {
        return new Builder();
    }

    @JsonPOJOBuilder(withPrefix = "")
    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public static final class Builder {

        private String id;
        private Integer created;
        private String model;
        private List<CompletionChoice> choices;
        private Usage usage;

        public Builder id(String id) {
            this.id = id;
            return this;
        }

        public Builder created(Integer created) {
            this.created = created;
            return this;
        }

        public Builder model(String model) {
            this.model = model;
            return this;
        }

        public Builder choices(List<CompletionChoice> choices) {
            if (choices != null) {
                this.choices = unmodifiableList(choices);
            }
            return this;
        }

        public Builder usage(Usage usage) {
            this.usage = usage;
            return this;
        }

        public CompletionResponse build() {
            return new CompletionResponse(this);
        }
    }
}
