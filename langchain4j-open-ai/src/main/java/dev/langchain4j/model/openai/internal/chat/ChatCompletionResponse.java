package dev.langchain4j.model.openai.internal.chat;

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

@JsonDeserialize(builder = ChatCompletionResponse.Builder.class)
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public final class ChatCompletionResponse {

    @JsonProperty
    private final String id;
    @JsonProperty
    private final Long created;
    @JsonProperty
    private final String model;
    @JsonProperty
    private final List<ChatCompletionChoice> choices;
    @JsonProperty
    private final Usage usage;
    @JsonProperty
    private final String systemFingerprint;
    @JsonProperty
    private final String serviceTier;

    public ChatCompletionResponse(Builder builder) {
        this.id = builder.id;
        this.created = builder.created;
        this.model = builder.model;
        this.choices = builder.choices;
        this.usage = builder.usage;
        this.systemFingerprint = builder.systemFingerprint;
        this.serviceTier = builder.serviceTier;
    }

    public String id() {
        return id;
    }

    public Long created() {
        return created;
    }

    public String model() {
        return model;
    }

    public List<ChatCompletionChoice> choices() {
        return choices;
    }

    public Usage usage() {
        return usage;
    }

    public String systemFingerprint() {
        return systemFingerprint;
    }

    public String serviceTier() {
        return serviceTier;
    }

    /**
     * Convenience method to get the content of the message from the first choice.
     */
    public String content() {
        return choices().get(0).message().content();
    }

    @Override
    public boolean equals(Object another) {
        if (this == another) return true;
        return another instanceof ChatCompletionResponse
                && equalTo((ChatCompletionResponse) another);
    }

    private boolean equalTo(ChatCompletionResponse another) {
        return Objects.equals(id, another.id)
                && Objects.equals(created, another.created)
                && Objects.equals(model, another.model)
                && Objects.equals(choices, another.choices)
                && Objects.equals(usage, another.usage)
                && Objects.equals(systemFingerprint, another.systemFingerprint)
                && Objects.equals(serviceTier, another.serviceTier);
    }

    @Override
    public int hashCode() {
        int h = 5381;
        h += (h << 5) + Objects.hashCode(id);
        h += (h << 5) + Objects.hashCode(created);
        h += (h << 5) + Objects.hashCode(model);
        h += (h << 5) + Objects.hashCode(choices);
        h += (h << 5) + Objects.hashCode(usage);
        h += (h << 5) + Objects.hashCode(systemFingerprint);
        h += (h << 5) + Objects.hashCode(serviceTier);
        return h;
    }

    @Override
    public String toString() {
        return "ChatCompletionResponse{"
                + "id=" + id
                + ", created=" + created
                + ", model=" + model
                + ", choices=" + choices
                + ", usage=" + usage
                + ", systemFingerprint=" + systemFingerprint
                + ", serviceTier=" + serviceTier
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
        private Long created;
        private String model;
        private List<ChatCompletionChoice> choices;
        private Usage usage;
        private String systemFingerprint;
        private String serviceTier;

        public Builder id(String id) {
            this.id = id;
            return this;
        }

        public Builder created(Long created) {
            this.created = created;
            return this;
        }

        public Builder model(String model) {
            this.model = model;
            return this;
        }

        public Builder choices(List<ChatCompletionChoice> choices) {
            if (choices != null) {
                this.choices = unmodifiableList(choices);
            }
            return this;
        }

        public Builder usage(Usage usage) {
            this.usage = usage;
            return this;
        }

        public Builder systemFingerprint(String systemFingerprint) {
            this.systemFingerprint = systemFingerprint;
            return this;
        }

        public Builder serviceTier(String serviceTier) {
            this.serviceTier = serviceTier;
            return this;
        }

        public ChatCompletionResponse build() {
            return new ChatCompletionResponse(this);
        }
    }
}
