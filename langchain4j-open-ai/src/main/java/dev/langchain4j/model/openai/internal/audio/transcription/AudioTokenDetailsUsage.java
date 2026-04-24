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

@JsonDeserialize(builder = AudioTokenDetailsUsage.Builder.class)
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class AudioTokenDetailsUsage {

    @JsonProperty
    private final Integer textTokens;

    @JsonProperty
    private final Integer audioTokens;

    public AudioTokenDetailsUsage(Builder builder) {
        this.textTokens = builder.textTokens;
        this.audioTokens = builder.audioTokens;
    }

    public Integer textTokens() {
        return textTokens;
    }

    public Integer audioTokens() {
        return audioTokens;
    }

    @Override
    @JacocoIgnoreCoverageGenerated
    public boolean equals(Object another) {
        if (this == another) return true;
        return another instanceof AudioTokenDetailsUsage audioTokenDetailsUsage && equalTo(audioTokenDetailsUsage);
    }

    @JacocoIgnoreCoverageGenerated
    private boolean equalTo(AudioTokenDetailsUsage another) {
        return Objects.equals(textTokens, another.textTokens) && Objects.equals(audioTokens, another.audioTokens);
    }

    @Override
    @JacocoIgnoreCoverageGenerated
    public int hashCode() {
        int h = 5381;
        h += (h << 5) + Objects.hashCode(textTokens);
        h += (h << 5) + Objects.hashCode(audioTokens);
        return h;
    }

    @Override
    @JacocoIgnoreCoverageGenerated
    public String toString() {
        return "AudioTokenDetailsUsage {" + " textTokens = " + textTokens + ", audioTokens = " + audioTokens + " }";
    }

    public static AudioTokenDetailsUsage.Builder builder() {
        return new AudioTokenDetailsUsage.Builder();
    }

    @JsonPOJOBuilder(withPrefix = "")
    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public static class Builder {

        private Integer textTokens;
        private Integer audioTokens;

        public AudioTokenDetailsUsage.Builder textTokens(Integer textTokens) {
            this.textTokens = textTokens;
            return this;
        }

        public AudioTokenDetailsUsage.Builder audioTokens(Integer audioTokens) {
            this.audioTokens = audioTokens;
            return this;
        }

        public AudioTokenDetailsUsage build() {
            return new AudioTokenDetailsUsage(this);
        }
    }
}
