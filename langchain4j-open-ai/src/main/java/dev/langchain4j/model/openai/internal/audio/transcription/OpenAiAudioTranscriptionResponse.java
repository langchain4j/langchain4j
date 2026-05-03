package dev.langchain4j.model.openai.internal.audio.transcription;

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

@JsonDeserialize(builder = OpenAiAudioTranscriptionResponse.Builder.class)
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public final class OpenAiAudioTranscriptionResponse {

    @JsonProperty
    private final String text;

    @JsonProperty
    private final AudioTokenUsage usage;

    @JsonProperty
    private final List<OpenAiAudioTranscriptionSegment> segments;

    @JsonProperty
    private final List<OpenAiAudioTranscriptionWord> words;

    public OpenAiAudioTranscriptionResponse(Builder builder) {
        this.text = builder.text;
        this.usage = builder.usage;
        this.segments = builder.segments;
        this.words = builder.words;
    }

    public String text() {
        return text;
    }

    public AudioTokenUsage usage() {
        return usage;
    }

    public List<OpenAiAudioTranscriptionSegment> segments() {
        return segments;
    }

    public List<OpenAiAudioTranscriptionWord> words() {
        return words;
    }

    @Override
    @JacocoIgnoreCoverageGenerated
    public boolean equals(Object another) {
        if (this == another) return true;
        return another instanceof OpenAiAudioTranscriptionResponse openAiAudioTranscriptionResponse
                && equalTo(openAiAudioTranscriptionResponse);
    }

    @JacocoIgnoreCoverageGenerated
    private boolean equalTo(OpenAiAudioTranscriptionResponse another) {
        return Objects.equals(text, another.text)
                && Objects.equals(usage, another.usage)
                && Objects.equals(segments, another.segments)
                && Objects.equals(words, another.words);
    }

    @Override
    @JacocoIgnoreCoverageGenerated
    public int hashCode() {
        int h = 5381;
        h += (h << 5) + Objects.hashCode(text);
        h += (h << 5) + Objects.hashCode(usage);
        h += (h << 5) + Objects.hashCode(segments);
        h += (h << 5) + Objects.hashCode(words);
        return h;
    }

    @Override
    @JacocoIgnoreCoverageGenerated
    public String toString() {
        return "OpenAiAudioTranscriptionResponse{" + "text=" + text + ", usage=" + usage + ", segments=" + segments
                + ", words=" + words + "}";
    }

    public static Builder builder() {
        return new Builder();
    }

    @JsonPOJOBuilder(withPrefix = "")
    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public static final class Builder {

        private String text;
        private AudioTokenUsage usage;
        private List<OpenAiAudioTranscriptionSegment> segments;
        private List<OpenAiAudioTranscriptionWord> words;

        public Builder text(String text) {
            this.text = text;
            return this;
        }

        public Builder usage(AudioTokenUsage usage) {
            this.usage = usage;
            return this;
        }

        public Builder segments(List<OpenAiAudioTranscriptionSegment> segments) {
            this.segments = segments;
            return this;
        }

        public Builder words(List<OpenAiAudioTranscriptionWord> words) {
            this.words = words;
            return this;
        }

        public OpenAiAudioTranscriptionResponse build() {
            return new OpenAiAudioTranscriptionResponse(this);
        }
    }
}
