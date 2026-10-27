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
 * Represents the response of the delete voice consent API.
 * Find description of fields
 * <a href="https://developers.openai.com/api/reference/resources/audio/subresources/voice_consents/methods/delete">here</a>.
 */
@JsonDeserialize(builder = OpenAiDeleteVoiceConsentResponse.Builder.class)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class OpenAiDeleteVoiceConsentResponse {

    /**
     * The consent recording identifier.
     */
    @JsonProperty
    private final String id;

    /**
     * Whether the consent recording was deleted.
     */
    @JsonProperty
    private final Boolean deleted;

    /**
     * The object type, which is always audio.voice_consent.
     */
    @JsonProperty
    private final String object;

    @JsonCreator
    public OpenAiDeleteVoiceConsentResponse(Builder builder) {
        this.id = builder.id;
        this.deleted = builder.deleted;
        this.object = builder.object;
    }

    public String id() {
        return id;
    }

    public Boolean deleted() {
        return deleted;
    }

    public String object() {
        return object;
    }

    @Override
    @JacocoIgnoreCoverageGenerated
    public boolean equals(Object another) {
        if (this == another) return true;
        return another instanceof OpenAiDeleteVoiceConsentResponse response
                && Objects.equals(id, response.id)
                && Objects.equals(deleted, response.deleted)
                && Objects.equals(object, response.object);
    }

    @Override
    @JacocoIgnoreCoverageGenerated
    public int hashCode() {
        return Objects.hash(id, deleted, object);
    }

    @Override
    @JacocoIgnoreCoverageGenerated
    public String toString() {
        return "OpenAiDeleteVoiceConsentResponse{"
                + " id = " + quoted(id)
                + ", deleted = " + deleted
                + ", object = " + quoted(object)
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
        private Boolean deleted;
        private String object;

        public Builder id(String id) {
            this.id = id;
            return this;
        }

        public Builder deleted(Boolean deleted) {
            this.deleted = deleted;
            return this;
        }

        public Builder object(String object) {
            this.object = object;
            return this;
        }

        public OpenAiDeleteVoiceConsentResponse build() {
            return new OpenAiDeleteVoiceConsentResponse(this);
        }
    }
}
