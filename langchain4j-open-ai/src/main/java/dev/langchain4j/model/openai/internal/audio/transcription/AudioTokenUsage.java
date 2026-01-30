package dev.langchain4j.model.openai.internal.audio.transcription;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import dev.langchain4j.internal.JacocoIgnoreCoverageGenerated;

import java.util.Objects;

import static dev.langchain4j.internal.Utils.quoted;

@JsonDeserialize(builder = AudioTokenUsage.Builder.class)
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class AudioTokenUsage {

    @JsonProperty
    private final String type;

    @JsonProperty
    private final Integer totalTokens;

    @JsonProperty
    private final Integer inputTokens;

    @JsonProperty
    private final AudioTokenDetailsUsage inputTokenDetails;

    @JsonProperty
    private final Integer outputTokens;

    public AudioTokenUsage(Builder builder) {
        this.type = builder.type;
        this.totalTokens = builder.totalTokens;
        this.inputTokens = builder.inputTokens;
        this.inputTokenDetails = builder.inputTokenDetails;
        this.outputTokens = builder.outputTokens;
    }

    public String type() {
        return type;
    }

    public Integer totalTokens() {
        return totalTokens;
    }

    public Integer inputTokens() {
        return inputTokens;
    }

    public AudioTokenDetailsUsage inputTokenDetails() {
        return inputTokenDetails;
    }

    public Integer outputTokens() {
        return outputTokens;
    }

    @Override
    @JacocoIgnoreCoverageGenerated
    public boolean equals(Object another) {
        if (this == another) return true;
        return another instanceof AudioTokenUsage audioTokenUsage && equalTo(audioTokenUsage);
    }

    @JacocoIgnoreCoverageGenerated
    private boolean equalTo(AudioTokenUsage another) {
        return Objects.equals(type, another.type)
                && Objects.equals(totalTokens, another.totalTokens)
                && Objects.equals(inputTokens, another.inputTokens)
                && Objects.equals(inputTokenDetails, another.inputTokenDetails)
                && Objects.equals(outputTokens, another.outputTokens);
    }

    @Override
    @JacocoIgnoreCoverageGenerated
    public int hashCode() {
        int h = 5381;
        h += (h << 5) + Objects.hashCode(type);
        h += (h << 5) + Objects.hashCode(totalTokens);
        h += (h << 5) + Objects.hashCode(inputTokens);
        h += (h << 5) + Objects.hashCode(inputTokenDetails);
        h += (h << 5) + Objects.hashCode(outputTokens);
        return h;
    }

    @Override
    @JacocoIgnoreCoverageGenerated
    public String toString() {
        return "AudioTokenUsage{"
                + " type = " + quoted(type)
                + ", totalTokens = " + totalTokens
                + " inputTokens = " + inputTokens
                + ", inputTokenDetails = " + inputTokenDetails
                + " outputTokens = " + outputTokens
                + "}";
    }

    public static AudioTokenUsage.Builder builder() {
        return new AudioTokenUsage.Builder();
    }

    @JsonPOJOBuilder(withPrefix = "")
    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public static class Builder {

        private String type;
        private Integer totalTokens;
        private Integer inputTokens;
        private AudioTokenDetailsUsage inputTokenDetails;
        private Integer outputTokens;

        public AudioTokenUsage.Builder type(String type) {
            this.type = type;
            return this;
        }

        public AudioTokenUsage.Builder totalTokens(Integer totalTokens) {
            this.totalTokens = totalTokens;
            return this;
        }

        public AudioTokenUsage.Builder inputTokens(Integer inputTokens) {
            this.inputTokens = inputTokens;
            return this;
        }

        public AudioTokenUsage.Builder inputTokenDetails(AudioTokenDetailsUsage inputTokenDetails) {
            this.inputTokenDetails = inputTokenDetails;
            return this;
        }

        public AudioTokenUsage.Builder outputTokens(Integer outputTokens) {
            this.outputTokens = outputTokens;
            return this;
        }

        public AudioTokenUsage build() {
            return new AudioTokenUsage(this);
        }
    }
}
