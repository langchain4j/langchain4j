package dev.langchain4j.model.audio;

import static dev.langchain4j.internal.Utils.quoted;
import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;

import dev.langchain4j.Experimental;
import dev.langchain4j.internal.JacocoIgnoreCoverageGenerated;
import java.util.Objects;

@Experimental
public class TextToSpeechRequest {

    private final String text;
    private final String voice;

    private TextToSpeechRequest(Builder builder) {
        this.text = ensureNotBlank(builder.text, "text");
        this.voice = builder.voice;
    }

    public String text() {
        return text;
    }

    public String voice() {
        return voice;
    }

    @Override
    @JacocoIgnoreCoverageGenerated
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TextToSpeechRequest that = (TextToSpeechRequest) o;
        return Objects.equals(this.text, that.text) && Objects.equals(this.voice, that.voice);
    }

    @Override
    @JacocoIgnoreCoverageGenerated
    public int hashCode() {
        return Objects.hash(text, voice);
    }

    @Override
    @JacocoIgnoreCoverageGenerated
    public String toString() {
        return "TextToSpeechRequest {" + " text = " + quoted(text) + ", voice = " + quoted(voice) + " }";
    }

    public static Builder builder() {
        return new Builder();
    }

    public static Builder builder(String text) {
        return new Builder().text(text);
    }

    public static class Builder {
        private String text;
        private String voice;

        public Builder text(String text) {
            this.text = text;
            return this;
        }

        public Builder voice(String voice) {
            this.voice = voice;
            return this;
        }

        public TextToSpeechRequest build() {
            return new TextToSpeechRequest(this);
        }
    }
}
