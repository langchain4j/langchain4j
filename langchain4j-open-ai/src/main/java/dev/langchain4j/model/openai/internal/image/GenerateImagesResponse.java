package dev.langchain4j.model.openai.internal.image;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import dev.langchain4j.internal.JacocoIgnoreCoverageGenerated;
import java.util.List;
import java.util.Objects;

/**
 * Represents the response from the OpenAI image generation API.
 * Find description of parameters <a href="https://developers.openai.com/api/reference/resources/images/methods/generate">here</a>.
 */
@JsonDeserialize(builder = GenerateImagesResponse.Builder.class)
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class GenerateImagesResponse {

    @JsonProperty
    private final Long created;

    @JsonProperty
    private final List<ImageData> data;

    @JsonProperty
    private final String background;

    @JsonProperty
    private final String outputFormat;

    @JsonProperty
    private final String quality;

    @JsonProperty
    private final String size;

    @JsonProperty
    private final ImageUsage usage;

    public GenerateImagesResponse(Builder builder) {
        this.created = builder.created;
        this.data = builder.data;
        this.background = builder.background;
        this.outputFormat = builder.outputFormat;
        this.quality = builder.quality;
        this.size = builder.size;
        this.usage = builder.usage;
    }

    public static Builder builder() {
        return new Builder();
    }

    public Long created() {
        return created;
    }

    public List<ImageData> data() {
        return data;
    }

    public String background() {
        return background;
    }

    public String outputFormat() {
        return outputFormat;
    }

    public String quality() {
        return quality;
    }

    public String size() {
        return size;
    }

    public ImageUsage usage() {
        return usage;
    }

    @Override
    @JacocoIgnoreCoverageGenerated
    public String toString() {
        return "GenerateImagesResponse{"
                + "created=" + created
                + ", data=" + data
                + ", background=" + background
                + ", outputFormat=" + outputFormat
                + ", quality=" + quality
                + ", size=" + size
                + ", usage=" + usage
                + '}';
    }

    @Override
    @JacocoIgnoreCoverageGenerated
    public boolean equals(Object another) {
        if (this == another) return true;
        if (another == null || getClass() != another.getClass()) return false;
        GenerateImagesResponse that = (GenerateImagesResponse) another;
        return Objects.equals(created, that.created)
                && Objects.equals(data, that.data)
                && Objects.equals(background, that.background)
                && Objects.equals(outputFormat, that.outputFormat)
                && Objects.equals(quality, that.quality)
                && Objects.equals(size, that.size)
                && Objects.equals(usage, that.usage);
    }

    @Override
    @JacocoIgnoreCoverageGenerated
    public int hashCode() {
        return Objects.hash(created, data, background, outputFormat, quality, size, usage);
    }

    @JsonPOJOBuilder(withPrefix = "")
    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public static class Builder {

        private Long created;
        private List<ImageData> data;
        private String background;
        private String outputFormat;
        private String quality;
        private String size;
        private ImageUsage usage;

        public Builder created(Long created) {
            this.created = created;
            return this;
        }

        public Builder data(List<ImageData> data) {
            this.data = data;
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

        public Builder quality(String quality) {
            this.quality = quality;
            return this;
        }

        public Builder size(String size) {
            this.size = size;
            return this;
        }

        public Builder usage(ImageUsage usage) {
            this.usage = usage;
            return this;
        }

        public GenerateImagesResponse build() {
            return new GenerateImagesResponse(this);
        }
    }
}
