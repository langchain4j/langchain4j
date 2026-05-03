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

@JsonDeserialize(builder = OpenAiAudioTranscriptionWord.Builder.class)
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public final class OpenAiAudioTranscriptionWord {

    @JsonProperty
    private final String word;

    @JsonProperty
    private final Double start;

    @JsonProperty
    private final Double end;

    public OpenAiAudioTranscriptionWord(Builder builder) {
        this.word = builder.word;
        this.start = builder.start;
        this.end = builder.end;
    }

    public String word() {
        return word;
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
        return another instanceof OpenAiAudioTranscriptionWord openAiAudioTranscriptionWord
                && equalTo(openAiAudioTranscriptionWord);
    }

    @JacocoIgnoreCoverageGenerated
    private boolean equalTo(OpenAiAudioTranscriptionWord another) {
        return Objects.equals(word, another.word)
                && Objects.equals(start, another.start)
                && Objects.equals(end, another.end);
    }

    @Override
    @JacocoIgnoreCoverageGenerated
    public int hashCode() {
        int h = 5381;
        h += (h << 5) + Objects.hashCode(word);
        h += (h << 5) + Objects.hashCode(start);
        h += (h << 5) + Objects.hashCode(end);
        return h;
    }

    @Override
    @JacocoIgnoreCoverageGenerated
    public String toString() {
        return "OpenAiAudioTranscriptionWord{" + "word=" + word + ", start=" + start + ", end=" + end + "}";
    }

    @JsonPOJOBuilder(withPrefix = "")
    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public static final class Builder {

        private String word;
        private Double start;
        private Double end;

        public Builder word(String word) {
            this.word = word;
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

        public OpenAiAudioTranscriptionWord build() {
            return new OpenAiAudioTranscriptionWord(this);
        }
    }
}
