package dev.langchain4j.model.openai.internal.image;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import dev.langchain4j.internal.JacocoIgnoreCoverageGenerated;
import java.util.Objects;

/**
 * Represents the request to the OpenAI image generation API.
 * Find description of parameters <a href="https://developers.openai.com/api/reference/resources/images/methods/generate">here</a>.
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
    private final String user;

    @JsonProperty
    private final String background;

    @JsonProperty
    private final String outputFormat;

    @JsonProperty
    private final Integer outputCompression;

    @JsonProperty
    private final String moderation;

    public GenerateImagesRequest(Builder builder) {
        this.model = builder.model;
        this.prompt = builder.prompt;
        this.n = builder.n;
        this.size = builder.size;
        this.quality = builder.quality;
        this.user = builder.user;
        this.background = builder.background;
        this.outputFormat = builder.outputFormat;
        this.outputCompression = builder.outputCompression;
        this.moderation = builder.moderation;
    }

    @Override
    @JacocoIgnoreCoverageGenerated
    public int hashCode() {
        int h = 2381;
        h += (h << 5) + Objects.hashCode(model);
        h += (h << 5) + Objects.hashCode(prompt);
        h += (h << 5) + n;
        h += (h << 5) + Objects.hashCode(size);
        h += (h << 5) + Objects.hashCode(quality);
        h += (h << 5) + Objects.hashCode(user);
        h += (h << 5) + Objects.hashCode(background);
        h += (h << 5) + Objects.hashCode(outputFormat);
        h += (h << 5) + Objects.hashCode(outputCompression);
        h += (h << 5) + Objects.hashCode(moderation);
        return h;
    }

    @Override
    @JacocoIgnoreCoverageGenerated
    public String toString() {
        return ("GenerateImagesRequest{" + "model="
                + model
                + ", prompt="
                + prompt
                + ", n="
                + n
                + ", size="
                + size
                + ", quality="
                + quality
                + ", user="
                + user
                + ", background="
                + background
                + ", outputFormat="
                + outputFormat
                + ", outputCompression="
                + outputCompression
                + ", moderation="
                + moderation
                + '}');
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
        private String user;
        private String background;
        private String outputFormat;
        private Integer outputCompression;
        private String moderation;

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

        public Builder user(String user) {
            this.user = user;
            return this;
        }

        public Builder background(String background) {
            this.background = background;
            return this;
        }

        public Builder outputFormat(String outputFormat) {
            this.outputFormat = outputFormat;
            return this;
        }

        public Builder outputCompression(Integer outputCompression) {
            this.outputCompression = outputCompression;
            return this;
        }

        public Builder moderation(String moderation) {
            this.moderation = moderation;
            return this;
        }

        public GenerateImagesRequest build() {
            return new GenerateImagesRequest(this);
        }
    }
}
