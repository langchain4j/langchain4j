package dev.langchain4j.model.audio;

import static dev.langchain4j.internal.Utils.copy;
import static java.util.Arrays.asList;

import dev.langchain4j.Experimental;
import dev.langchain4j.data.audio.Audio;
import java.util.List;

/**
 * Request to transcribe audio.
 */
@Experimental
public class AudioTranscriptionRequest {

    private final Audio audio;
    private final String prompt;
    private final String language;
    private final Double temperature;
    private final List<String> timestampGranularities;

    private AudioTranscriptionRequest(Builder builder) {
        this.audio = builder.audio;
        this.prompt = builder.prompt;
        this.language = builder.language;
        this.temperature = builder.temperature;
        this.timestampGranularities = copy(builder.timestampGranularities);
    }

    /**
     * @return Audio data to transcribe
     */
    public Audio audio() {
        return audio;
    }

    /**
     * @return An optional prompt to guide the model's transcription
     */
    public String prompt() {
        return prompt;
    }

    /**
     * @return An optional language code to use for the transcription
     */
    public String language() {
        return language;
    }

    /**
     * @return An optional temperature parameter for the transcription (0.0-1.0)
     */
    public Double temperature() {
        return temperature;
    }

    /**
     * @return Optional timestamp granularities to include in the transcription response.
     * <p>
     * This feature is disabled by default. When configured, integrations may request
     * a provider-specific verbose response format to return timestamps.
     * Supported values and model or deployment support are provider-specific.
     * If the selected model or deployment does not support the requested timestamp
     * granularity, the provider will return an error.
     */
    public List<String> timestampGranularities() {
        return timestampGranularities;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static Builder builder(Audio audio) {
        return new Builder().audio(audio);
    }

    public static class Builder {
        private Audio audio;
        private String prompt;
        private String language;
        private Double temperature;
        private List<String> timestampGranularities;

        /**
         * Sets the audio data to transcribe.
         *
         * @param audio The audio data
         * @return builder
         */
        public Builder audio(Audio audio) {
            this.audio = audio;
            return this;
        }

        /**
         * Sets an optional text prompt to guide the model's transcription.
         *
         * @param prompt The text prompt
         * @return builder
         */
        public Builder prompt(String prompt) {
            this.prompt = prompt;
            return this;
        }

        /**
         * Sets the language of the audio for more accurate transcription.
         *
         * @param language The language code (e.g., "en", "fr", "es")
         * @return builder
         */
        public Builder language(String language) {
            this.language = language;
            return this;
        }

        /**
         * Sets the temperature parameter for controlling randomness in the transcription.
         *
         * @param temperature A value between 0.0 and 1.0
         * @return builder
         */
        public Builder temperature(Double temperature) {
            this.temperature = temperature;
            return this;
        }

        /**
         * Sets optional timestamp granularities to include in the transcription response.
         * <p>
         * This feature is disabled by default. When configured, integrations may request
         * a provider-specific verbose response format to return timestamps.
         * Supported values and model or deployment support are provider-specific.
         * If the selected model or deployment does not support the requested timestamp
         * granularity, the provider will return an error.
         *
         * @param timestampGranularities The timestamp granularities, for example "word" or "segment"
         * @return builder
         */
        public Builder timestampGranularities(List<String> timestampGranularities) {
            this.timestampGranularities = timestampGranularities;
            return this;
        }

        /**
         * Sets optional timestamp granularities to include in the transcription response.
         *
         * @see #timestampGranularities(List)
         *
         * @param timestampGranularities The timestamp granularities, for example "word" or "segment"
         * @return builder
         */
        public Builder timestampGranularities(String... timestampGranularities) {
            return timestampGranularities(asList(timestampGranularities));
        }

        public AudioTranscriptionRequest build() {
            if (audio == null) {
                throw new IllegalStateException("Audio must be provided");
            }
            return new AudioTranscriptionRequest(this);
        }
    }
}
