package dev.langchain4j.model.openai.internal.audio.texttospeech;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

/**
 * Represents the text-to-speech request.
 * Find description of parameters
 * <a href="https://developers.openai.com/api/reference/resources/audio/subresources/speech/methods/create">here</a>.
 */
@JsonDeserialize(builder = OpenAiTextToSpeechRequest.Builder.class)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class OpenAiTextToSpeechRequest {

    /**
     * The text to generate audio for. The maximum length is 4096 characters.
     */
    @JsonProperty
    private final String input;

    /**
     * ID of the model to use. The options are : <br/>
     * TTS models: tts-1, tts-1-hd, gpt-4o-mini-tts, or gpt-4o-mini-tts-2025-12-15.
     */
    @JsonProperty
    private final String model;

    /**
     * The voice to use when generating the audio.
     * Supported built-in voices are alloy, ash, ballad, coral, echo, fable, onyx, nova, sage, shimmer,
     * verse, marin, and cedar.
     */
    @JsonProperty
    private final String voice;

    public OpenAiTextToSpeechRequest(Builder builder) {
        this.input = builder.text;
        this.model = builder.model;
        this.voice = builder.voice;
    }

    public String text() {
        return input;
    }

    public String model() {
        return model;
    }

    public String voice() {
        return voice;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private String text;
        private String model;
        private String voice;

        public Builder text(String text) {
            this.text = text;
            return this;
        }

        public Builder model(String model) {
            this.model = model;
            return this;
        }

        public Builder voice(String voice) {
            this.voice = voice;
            return this;
        }

        public OpenAiTextToSpeechRequest build() {
            return new OpenAiTextToSpeechRequest(this);
        }
    }
}
