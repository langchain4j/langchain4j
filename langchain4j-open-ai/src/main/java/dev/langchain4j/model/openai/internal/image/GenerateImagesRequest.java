package dev.langchain4j.model.openai.internal.image;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;

import java.util.Objects;

/**
 * Represents the request from the OpenAI DALL·E API when generating images.
 * Find description of parameters <a href="https://platform.openai.com/docs/api-reference/images/create">here</a>.
 */
@JsonDeserialize(builder = GenerateImagesRequest.Builder.class)
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class GenerateImagesRequest {

    @JsonProperty
    private final String model;
    @JsonProperty
    private final String prompt;
    @JsonProperty
    private final int n;
    @JsonProperty
    private final String size;
    @JsonProperty
    private final String quality;
    @JsonProperty
    private final String style;
    @JsonProperty
    private final String user;
    @JsonProperty
    private final String responseFormat;

    public GenerateImagesRequest(Builder builder) {
        this.model = builder.model;
        this.prompt = builder.prompt;
        this.n = builder.n;
        this.size = builder.size;
        this.quality = builder.quality;
        this.style = builder.style;
        this.user = builder.user;
        this.responseFormat = builder.responseFormat;
    }

    public int hashCode() {
        int h = 2381;
        h += (h << 5) + Objects.hashCode(model);
        h += (h << 5) + Objects.hashCode(prompt);
        h += (h << 5) + n;
        h += (h << 5) + Objects.hashCode(size);
        h += (h << 5) + Objects.hashCode(quality);
        h += (h << 5) + Objects.hashCode(style);
        h += (h << 5) + Objects.hashCode(user);
        h += (h << 5) + Objects.hashCode(responseFormat);
        return h;
    }

    public String toString() {
        return (
                "GenerateImagesRequest{" +
                        "model=" +
                        model +
                        ", prompt=" +
                        prompt +
                        ", n=" +
                        n +
                        ", size=" +
                        size +
                        ", quality=" +
                        quality +
                        ", style=" +
                        style +
                        ", user=" +
                        user +
                        ", responseFormat=" +
                        responseFormat +
                        '}'
        );
    }

    public static Builder builder() {
        return new Builder();
    }

    @JsonPOJOBuilder(withPrefix = "")
    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public static class Builder {

        private String model;
        private String prompt;
        private int n = 1;
        private String size;
        private String quality;
        private String style;
        private String user;
        private String responseFormat;

        public Builder model(String model) {
            this.model = model;
            return this;
        }

        public Builder prompt(String prompt) {
            this.prompt = prompt;
            return this;
        }

        public Builder n(int n) {
            this.n = n;
            return this;
        }

        public Builder size(String size) {
            this.size = size;
            return this;
        }

        public Builder quality(String quality) {
            this.quality = quality;
            return this;
        }

        public Builder style(String style) {
            this.style = style;
            return this;
        }

        public Builder user(String user) {
            this.user = user;
            return this;
        }

        public Builder responseFormat(String responseFormat) {
            this.responseFormat = responseFormat;
            return this;
        }

        public GenerateImagesRequest build() {
            return new GenerateImagesRequest(this);
        }
    }
}
