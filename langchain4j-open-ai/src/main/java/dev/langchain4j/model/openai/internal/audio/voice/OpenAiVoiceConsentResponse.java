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
 * A voice consent recording.
 * Its {@code id} is referenced when creating a custom voice,
 * and can be reused for several voice creations by the same voice actor.
 * Find description of fields
 * <a href="https://developers.openai.com/api/reference/resources/audio/subresources/voice_consents/methods/create">here</a>.
 */
@JsonDeserialize(builder = OpenAiVoiceConsentResponse.Builder.class)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class OpenAiVoiceConsentResponse {

    /**
     * The consent recording identifier, e.g. cons_1234.
     */
    @JsonProperty
    private final String id;

    /**
     * The Unix timestamp (in seconds) for when the consent recording was created.
     */
    @JsonProperty
    private final Long createdAt;

    /**
     * The BCP 47 language tag for the consent phrase (for example, en-US).
     */
    @JsonProperty
    private final String language;

    /**
     * The label provided when the consent recording was uploaded.
     */
    @JsonProperty
    private final String name;

    /**
     * The object type, which is always audio.voice_consent.
     */
    @JsonProperty
    private final String object;

    @JsonCreator
    public OpenAiVoiceConsentResponse(Builder builder) {
        this.id = builder.id;
        this.object = builder.object;
        this.name = builder.name;
        this.language = builder.language;
        this.createdAt = builder.createdAt;
    }

    public String id() {
        return id;
    }

    public String object() {
        return object;
    }

    public String name() {
        return name;
    }

    public String language() {
        return language;
    }

    public Long createdAt() {
        return createdAt;
    }

    @Override
    @JacocoIgnoreCoverageGenerated
    public boolean equals(Object another) {
        if (this == another) return true;
        return another instanceof OpenAiVoiceConsentResponse consent
                && Objects.equals(id, consent.id)
                && Objects.equals(object, consent.object)
                && Objects.equals(name, consent.name)
                && Objects.equals(language, consent.language)
                && Objects.equals(createdAt, consent.createdAt);
    }

    @Override
    @JacocoIgnoreCoverageGenerated
    public int hashCode() {
        return Objects.hash(id, object, name, language, createdAt);
    }

    @Override
    @JacocoIgnoreCoverageGenerated
    public String toString() {
        return "OpenAiVoiceConsent{"
                + " id = " + quoted(id)
                + ", object = " + quoted(object)
                + ", name = " + quoted(name)
                + ", language = " + quoted(language)
                + ", createdAt = " + createdAt
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
        private String object;
        private String name;
        private String language;
        private Long createdAt;

        public Builder id(String id) {
            this.id = id;
            return this;
        }

        public Builder object(String object) {
            this.object = object;
            return this;
        }

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder language(String language) {
            this.language = language;
            return this;
        }

        public Builder createdAt(Long createdAt) {
            this.createdAt = createdAt;
            return this;
        }

        public OpenAiVoiceConsentResponse build() {
            return new OpenAiVoiceConsentResponse(this);
        }
    }
}
