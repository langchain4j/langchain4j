package dev.langchain4j.model.openai.internal.audio.voice;

import static dev.langchain4j.internal.Utils.quoted;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import dev.langchain4j.internal.JacocoIgnoreCoverageGenerated;
import java.util.Objects;

/**
 * A custom voice that can be used for audio output, as returned by the create voice API.
 * Its {@code id} can be passed as the custom voice of a text-to-speech request.
 * Find description of fields
 * <a href="https://developers.openai.com/api/reference/resources/audio/subresources/voices/methods/create">here</a>.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonDeserialize(builder = OpenAiCreateVoiceResponse.Builder.class)
public class OpenAiCreateVoiceResponse {

    /**
     * The voice identifier, which can be referenced in API endpoints.
     */
    @JsonProperty
    private final String id;

    /**
     * The Unix timestamp (in seconds) for when the voice was created.
     */
    @JsonProperty
    private final Long createdAt;

    /**
     * The name of the voice.
     */
    @JsonProperty
    private final String name;

    /**
     * The object type, which is always audio.voice.
     */
    @JsonProperty
    private final String object;

    /**
     * How the voice was created, either audio_sample or prompt.
     */
    @JsonProperty
    private final String type;

    /**
     * The original text prompt used to create the voice. Present for prompt-generated voices with a stored prompt;
     * omitted for audio-sample voices and older voices without a stored prompt.
     */
    @JsonProperty
    private final String prompt;

    @JsonCreator
    public OpenAiCreateVoiceResponse(Builder builder) {
        this.id = builder.id;
        this.createdAt = builder.createdAt;
        this.name = builder.name;
        this.object = builder.object;
        this.type = builder.type;
        this.prompt = builder.prompt;
    }

    public String id() {
        return id;
    }

    public Long createdAt() {
        return createdAt;
    }

    public String name() {
        return name;
    }

    public String object() {
        return object;
    }

    public String type() {
        return type;
    }

    public String prompt() {
        return prompt;
    }

    @Override
    @JacocoIgnoreCoverageGenerated
    public boolean equals(Object another) {
        if (this == another) return true;
        return another instanceof OpenAiCreateVoiceResponse voice
                && Objects.equals(id, voice.id)
                && Objects.equals(createdAt, voice.createdAt)
                && Objects.equals(name, voice.name)
                && Objects.equals(object, voice.object)
                && Objects.equals(type, voice.type)
                && Objects.equals(prompt, voice.prompt);
    }

    @Override
    @JacocoIgnoreCoverageGenerated
    public int hashCode() {
        return Objects.hash(id, createdAt, name, object, type, prompt);
    }

    @Override
    @JacocoIgnoreCoverageGenerated
    public String toString() {
        return "OpenAiCreateVoiceResponse{"
                + " id = " + quoted(id)
                + ", createdAt = " + createdAt
                + ", name = " + quoted(name)
                + ", object = " + quoted(object)
                + ", type = " + quoted(type)
                + ", prompt = " + quoted(prompt)
                + " }";
    }

    public static Builder builder() {
        return new Builder();
    }

    @JsonPOJOBuilder(withPrefix = "")
    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
    public static class Builder {

        private String id;
        private Long createdAt;
        private String name;
        private String object;
        private String type;
        private String prompt;

        public Builder id(String id) {
            this.id = id;
            return this;
        }

        public Builder createdAt(Long createdAt) {
            this.createdAt = createdAt;
            return this;
        }

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder object(String object) {
            this.object = object;
            return this;
        }

        public Builder type(String type) {
            this.type = type;
            return this;
        }

        public Builder prompt(String prompt) {
            this.prompt = prompt;
            return this;
        }

        public OpenAiCreateVoiceResponse build() {
            return new OpenAiCreateVoiceResponse(this);
        }
    }
}
