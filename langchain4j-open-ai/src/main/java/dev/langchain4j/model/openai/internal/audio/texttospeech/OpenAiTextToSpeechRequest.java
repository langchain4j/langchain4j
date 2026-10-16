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
    private final String input;

    /**
     * ID of the model to use. The options are : <br/>
     * TTS models: tts-1, tts-1-hd, gpt-4o-mini-tts, or gpt-4o-mini-tts-2025-12-15.
     */
    @JsonProperty
    private final String model;

    /**
     * The voice to use when generating the audio. Either the name of a built-in voice ({@link String})
     * or a custom voice ({@link CustomVoice}).
     * Supported built-in voices are alloy, ash, ballad, coral, echo, fable, onyx, nova, sage, shimmer,
     * verse, marin, and cedar.
     */
    @JsonProperty
    private final Object voice;

    /**
     * Control the voice of your generated audio with additional instructions.
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
     * The speed of the generated audio. Select a value from 0.25 to 4.0. 1.0 is the default.
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
        this.input = builder.text;
        this.model = builder.model;
        this.voice = builder.voice;
        this.instructions = builder.instructions;
        this.responseFormat = builder.responseFormat;
        this.speed = builder.speed;
        this.streamFormat = builder.streamFormat;
    }

    public String text() {
        return input;
    }

    public String model() {
        return model;
    }

    /**
     * @return the name of a built-in voice ({@link String}) or a {@link CustomVoice}
     */
    public Object voice() {
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

    /**
     * A custom voice, referenced by its ID (e.g. {@code voice_1234}).
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class CustomVoice {

        @JsonProperty
        private final String id;

        public CustomVoice(String id) {
            this.id = id;
        }

        public String id() {
            return id;
        }
    }

    @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
    public static class Builder {

        private String text;
        private String model;
        private Object voice;
        private String instructions;
        private String responseFormat;
        private Double speed;
        private String streamFormat;

        public Builder text(String text) {
            this.text = text;
            return this;
        }

        public Builder model(String model) {
            this.model = model;
            return this;
        }

        /**
         * Sets a built-in voice by name.
         */
        public Builder voice(String voice) {
            this.voice = voice;
            return this;
        }

        /**
         * Sets a custom voice.
         */
        public Builder voice(CustomVoice voice) {
            this.voice = voice;
            return this;
        }

        /**
         * Sets a custom voice by its ID (e.g. {@code voice_1234}).
         */
        public Builder customVoiceId(String voiceId) {
            this.voice = voiceId == null ? null : new CustomVoice(voiceId);
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
