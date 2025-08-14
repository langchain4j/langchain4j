package dev.langchain4j.model.openai.internal.shared;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;

import java.util.Objects;

@JsonDeserialize(builder = StreamOptions.Builder.class)
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public final class StreamOptions {

    @JsonProperty
    private final Boolean includeUsage;

    public StreamOptions(Builder builder) {
        this.includeUsage = builder.includeUsage;
    }

    public Boolean includeUsage() {
        return includeUsage;
    }

    @Override
    public boolean equals(Object another) {
        if (this == another) return true;
        return another instanceof StreamOptions
                && equalTo((StreamOptions) another);
    }

    private boolean equalTo(StreamOptions another) {
        return Objects.equals(includeUsage, another.includeUsage);
    }

    @Override
    public int hashCode() {
        int h = 5381;
        h += (h << 5) + Objects.hashCode(includeUsage);
        return h;
    }

    @Override
    public String toString() {
        return "StreamOptions{" +
                "includeUsage=" + includeUsage +
                "}";
    }

    public static Builder builder() {
        return new Builder();
    }

    @JsonPOJOBuilder(withPrefix = "")
    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public static class Builder {

        private Boolean includeUsage;

        public Builder includeUsage(Boolean includeUsage) {
            this.includeUsage = includeUsage;
            return this;
        }

        public StreamOptions build() {
            return new StreamOptions(this);
        }
    }
}
