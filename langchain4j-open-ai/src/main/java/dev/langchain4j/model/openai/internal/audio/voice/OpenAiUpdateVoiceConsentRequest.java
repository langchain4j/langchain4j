package dev.langchain4j.model.openai.internal.audio.voice;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;

/**
 * Represents the request to update a voice consent recording (metadata only).
 * Find description of parameters
 * <a href="https://developers.openai.com/api/reference/resources/audio/subresources/voice_consents/methods/update">here</a>.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonDeserialize(builder = OpenAiUpdateVoiceConsentRequest.Builder.class)
public class OpenAiUpdateVoiceConsentRequest {

    /**
     * The ID of the consent recording to update (for example, cons_1234). Sent as a path parameter.
     */
    @JsonIgnore
    private final String consentId;

    /**
     * The updated label for this consent recording.
     */
    @JsonProperty
    private final String name;

    @JsonCreator
    public OpenAiUpdateVoiceConsentRequest(Builder builder) {
        this.consentId = builder.consentId;
        this.name = builder.name;
    }

    public String consentId() {
        return consentId;
    }

    public String name() {
        return name;
    }

    public static Builder builder() {
        return new Builder();
    }

    @JsonPOJOBuilder(withPrefix = "")
    @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
    public static class Builder {

        private String consentId;
        private String name;

        public Builder consentId(String consentId) {
            this.consentId = consentId;
            return this;
        }

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public OpenAiUpdateVoiceConsentRequest build() {
            return new OpenAiUpdateVoiceConsentRequest(this);
        }
    }
}
