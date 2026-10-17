package dev.langchain4j.model.openai.internal.audio.voice;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;

/**
 * Represents the request to create a custom voice from a text description, sent as a JSON body.
 * Find description of parameters
 * <a href="https://developers.openai.com/api/reference/resources/audio/subresources/voices/methods/create">here</a>.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonDeserialize(builder = OpenAiCreateVoiceRequest.Builder.class)
public class OpenAiCreateVoiceRequest {

    /**
     * Voices created from a text description always use the prompt type.
     */
    private static final String PROMPT_TYPE = "prompt";

    /**
     * The name of the new voice. Between 1 and 256 characters.
     */
    @JsonProperty
    private final String name;

    /**
     * A description of the desired voice. Between 1 and 2000 characters, must not contain only whitespace.
     */
    @JsonProperty
    private final String prompt;

    /**
     * The voice creation method, always prompt.
     */
    @JsonProperty
    private final String type;

    /**
     * The voice creation model to use, e.g. auto or 2026-10-08. Defaults to auto when omitted.
     */
    @JsonProperty
    private final String model;

    /**
     * Optional text for the voice to speak during creation. Between 1 and 2000 characters.
     * If omitted, a script is generated from the prompt.
     */
    @JsonProperty
    private final String scriptHint;

    @JsonCreator
    public OpenAiCreateVoiceRequest(Builder builder) {
        this.name = builder.name;
        this.prompt = builder.prompt;
        this.type = PROMPT_TYPE;
        this.model = builder.model;
        this.scriptHint = builder.scriptHint;
    }

    public String name() {
        return name;
    }

    public String prompt() {
        return prompt;
    }

    public String type() {
        return type;
    }

    public String model() {
        return model;
    }

    public String scriptHint() {
        return scriptHint;
    }

    public static Builder builder() {
        return new Builder();
    }

    @JsonPOJOBuilder(withPrefix = "")
    @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
    public static class Builder {

        private String name;
        private String prompt;
        private String model;
        private String scriptHint;

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder prompt(String prompt) {
            this.prompt = prompt;
            return this;
        }

        public Builder model(String model) {
            this.model = model;
            return this;
        }

        public Builder scriptHint(String scriptHint) {
            this.scriptHint = scriptHint;
            return this;
        }

        public OpenAiCreateVoiceRequest build() {
            return new OpenAiCreateVoiceRequest(this);
        }
    }
}
