package dev.langchain4j.model.openai.internal.audio.voice;

import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

/**
 * Identifies a voice consent recording.
 * Find description of parameters
 * <a href="https://developers.openai.com/api/reference/resources/audio/subresources/voice_consents/methods/retrieve">here</a>
 * and <a href="https://developers.openai.com/api/reference/resources/audio/subresources/voice_consents/methods/delete">here</a>.
 *
 * @param consentId The ID of the consent recording (for example, cons_1234).
 */
public record OpenAiVoiceConsentId(String consentId) {

    public OpenAiVoiceConsentId {
        ensureNotNull(consentId, "consentId");
    }

    public OpenAiVoiceConsentId(Builder consentId) {
        this(consentId.consentId);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private String consentId;

        public Builder consentId(String consentId) {
            this.consentId = consentId;
            return this;
        }

        public OpenAiVoiceConsentId build() {
            return new OpenAiVoiceConsentId(this);
        }
    }
}
