package dev.langchain4j.model.audio;

import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

import dev.langchain4j.Experimental;
import dev.langchain4j.data.audio.Audio;

/**
 * Request to generate speech audio directly from speech audio.
 */
@Experimental
public class SpeechToSpeechRequest {

    private final Audio audio;
    private final String instructions;
    private final String voice;

    private SpeechToSpeechRequest(Builder builder) {
        this.audio = ensureNotNull(builder.audio, "audio");
        this.instructions = builder.instructions;
        this.voice = builder.voice;
    }

    public Audio audio() {
        return audio;
    }

    public String instructions() {
        return instructions;
    }

    public String voice() {
        return voice;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static Builder builder(Audio audio) {
        return new Builder().audio(audio);
    }

    public static class Builder {
        private Audio audio;
        private String instructions;
        private String voice;

        public Builder audio(Audio audio) {
            this.audio = audio;
            return this;
        }

        public Builder instructions(String instructions) {
            this.instructions = instructions;
            return this;
        }

        public Builder voice(String voice) {
            this.voice = voice;
            return this;
        }

        public SpeechToSpeechRequest build() {
            return new SpeechToSpeechRequest(this);
        }
    }
}
