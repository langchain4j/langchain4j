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

@JsonDeserialize(builder = OpenAiAudioTranscriptionSegment.Builder.class)
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public final class OpenAiAudioTranscriptionSegment {

    @JsonProperty
    private final String text;

    @JsonProperty
    private final Double start;

    @JsonProperty
    private final Double end;

    public OpenAiAudioTranscriptionSegment(Builder builder) {
        this.text = builder.text;
        this.start = builder.start;
        this.end = builder.end;
    }

    public String text() {
        return text;
    }

    public Double start() {
        return start;
    }

    public Double end() {
        return end;
    }

    @Override
    @JacocoIgnoreCoverageGenerated
    public boolean equals(Object another) {
        if (this == another) return true;
        return another instanceof OpenAiAudioTranscriptionSegment openAiAudioTranscriptionSegment
                && equalTo(openAiAudioTranscriptionSegment);
    }

    @JacocoIgnoreCoverageGenerated
    private boolean equalTo(OpenAiAudioTranscriptionSegment another) {
        return Objects.equals(text, another.text)
                && Objects.equals(start, another.start)
                && Objects.equals(end, another.end);
    }

    @Override
    @JacocoIgnoreCoverageGenerated
    public int hashCode() {
        int h = 5381;
        h += (h << 5) + Objects.hashCode(text);
        h += (h << 5) + Objects.hashCode(start);
        h += (h << 5) + Objects.hashCode(end);
        return h;
    }

    @Override
    @JacocoIgnoreCoverageGenerated
    public String toString() {
        return "OpenAiAudioTranscriptionSegment{" + "text=" + text + ", start=" + start + ", end=" + end + "}";
    }

    @JsonPOJOBuilder(withPrefix = "")
    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public static final class Builder {

        private String text;
        private Double start;
        private Double end;

        public Builder text(String text) {
            this.text = text;
            return this;
        }

        public Builder start(Double start) {
            this.start = start;
            return this;
        }

        public Builder end(Double end) {
            this.end = end;
            return this;
        }

        public OpenAiAudioTranscriptionSegment build() {
            return new OpenAiAudioTranscriptionSegment(this);
        }
    }
}
