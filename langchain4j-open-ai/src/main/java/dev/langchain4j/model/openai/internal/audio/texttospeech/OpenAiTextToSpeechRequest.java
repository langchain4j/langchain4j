package dev.langchain4j.model.openai.internal.audio.texttospeech;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

/**
 * Represents the text-to-speech request.
 * Find description of parameters
 * <a href="https://developers.openai.com/api/reference/resources/audio/subresources/speech/methods/create">here</a>.
 */
@JsonDeserialize(builder = OpenAiTextToSpeechRequest.Builder.class)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class OpenAiTextToSpeechRequest {

    /**
     * The text to generate audio for. The maximum length is 4096 characters.
     */
    @JsonProperty
    private final String text;

    /**
     * ID of the model to use. The options are : <br/>
     * TTS models: tts-1, tts-1-hd, gpt-4o-mini-tts, or gpt-4o-mini-tts-2025-12-15.
     */
    @JsonProperty
    private final String model;

    /**
     * The voice to use when generating the audio: a built-in voice
     * (alloy, ash, ballad, coral, echo, fable, onyx, nova, sage, shimmer, verse, marin, or cedar)
     * or a custom voice.
     */
    @JsonProperty
    private final OpenAiVoice voice;

    /**
     * Control the voice of your generated audio with additional instructions (Optional).
     * Does not work with tts-1 or tts-1-hd.
     */
    @JsonProperty
    private final String instructions;

    /**
     * The format to return audio in. Supported formats are mp3, opus, aac, flac, wav, and pcm.
     */
    @JsonProperty
    private final String responseFormat;

    /**
     * The speed of the generated audio (Optional). Select a value from 0.25 to 4.0. 1.0 is the default.
     */
    @JsonProperty
    private final Double speed;

    /**
     * The format to stream the audio in. Supported formats are sse and audio.
     * sse is not supported for tts-1 or tts-1-hd.
     */
    @JsonProperty
    private final String streamFormat;

    @JsonCreator
    public OpenAiTextToSpeechRequest(Builder builder) {
        this.text = builder.text;
        this.model = builder.model;
        this.voice = builder.voice;
        this.instructions = builder.instructions;
        this.responseFormat = builder.responseFormat;
        this.speed = builder.speed;
        this.streamFormat = builder.streamFormat;
    }

    public String text() {
        return text;
    }

    public String model() {
        return model;
    }

    public OpenAiVoice voice() {
        return voice;
    }

    public String instructions() {
        return instructions;
    }

    public String responseFormat() {
        return responseFormat;
    }

    public Double speed() {
        return speed;
    }

    public String streamFormat() {
        return streamFormat;
    }

    public static Builder builder() {
        return new Builder();
    }

    @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
    public static class Builder {

        private String text;
        private String model;
        private OpenAiVoice voice;
        private String instructions;
        private String responseFormat;
        private Double speed;
        private String streamFormat;

        public Builder from(OpenAiTextToSpeechRequest request) {
            text(request.text);
            model(request.model);
            voice(request.voice);
            instructions(request.instructions);
            responseFormat(request.responseFormat);
            speed(request.speed);
            streamFormat(request.streamFormat);
            return this;
        }

        public Builder text(String text) {
            this.text = text;
            return this;
        }

        public Builder model(String model) {
            this.model = model;
            return this;
        }

        public Builder voice(OpenAiVoice voice) {
            this.voice = voice;
            return this;
        }

        public Builder instructions(String instructions) {
            this.instructions = instructions;
            return this;
        }

        public Builder responseFormat(String responseFormat) {
            this.responseFormat = responseFormat;
            return this;
        }

        public Builder speed(Double speed) {
            this.speed = speed;
            return this;
        }

        public Builder streamFormat(String streamFormat) {
            this.streamFormat = streamFormat;
            return this;
        }

        public OpenAiTextToSpeechRequest build() {
            return new OpenAiTextToSpeechRequest(this);
        }
    }
}
