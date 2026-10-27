package dev.langchain4j.model.openai.internal.audio.texttospeech;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import dev.langchain4j.model.openai.internal.audio.transcription.AudioTokenUsage;

/**
 * Represents an event of the OpenAI speech API when {@code stream_format} is {@code sse}:
 * <ul>
 *     <li>{@code speech.audio.delta}: carries a chunk of base64-encoded {@code audio}</li>
 *     <li>{@code speech.audio.done}: signals the end of the stream and carries the token {@code usage}</li>
 * </ul>
 */
@JsonDeserialize(builder = OpenAiTextToSpeechStreamEvent.Builder.class)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class OpenAiTextToSpeechStreamEvent {

    public static final String AUDIO_DELTA = "speech.audio.delta";
    public static final String AUDIO_DONE = "speech.audio.done";

    @JsonProperty
    private final String type;

    @JsonProperty
    private final String audio;

    @JsonProperty
    private final AudioTokenUsage usage;

    @JsonCreator
    public OpenAiTextToSpeechStreamEvent(Builder builder) {
        this.type = builder.type;
        this.audio = builder.audio;
        this.usage = builder.usage;
    }

    public String type() {
        return type;
    }

    /**
     * @return a chunk of base64-encoded audio, present on {@code speech.audio.delta} events
     */
    public String audio() {
        return audio;
    }

    /**
     * @return the token usage, present on {@code speech.audio.done} events
     */
    public AudioTokenUsage usage() {
        return usage;
    }

    public static Builder builder() {
        return new Builder();
    }

    @JsonPOJOBuilder(withPrefix = "")
    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
    public static class Builder {

        private String type;
        private String audio;
        private AudioTokenUsage usage;

        public Builder type(String type) {
            this.type = type;
            return this;
        }

        public Builder audio(String audio) {
            this.audio = audio;
            return this;
        }

        public Builder usage(AudioTokenUsage usage) {
            this.usage = usage;
            return this;
        }

        public OpenAiTextToSpeechStreamEvent build() {
            return new OpenAiTextToSpeechStreamEvent(this);
        }
    }
}
