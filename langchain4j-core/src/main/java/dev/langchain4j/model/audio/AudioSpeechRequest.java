package dev.langchain4j.model.audio;

import dev.langchain4j.Experimental;

import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;

@Experimental
public class AudioSpeechRequest {

    private final String text;
    private final String voice;

    private AudioSpeechRequest(Builder builder) {
        this.text = ensureNotBlank(builder.text, "text");
        this.voice = builder.voice;
    }

    public String text() {
        return text;
    }

    public String voice() {
        return voice;
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

        public AudioSpeechRequest build() {
            return new AudioSpeechRequest(this);
        }
    }
}
