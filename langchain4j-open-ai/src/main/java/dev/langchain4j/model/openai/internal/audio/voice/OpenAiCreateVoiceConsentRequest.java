package dev.langchain4j.model.openai.internal.audio.voice;

import dev.langchain4j.model.openai.internal.audio.transcription.AudioFile;

/**
 * Represents the request to upload a voice consent recording, sent as multipart form data.
 * Find description of parameters
 * <a href="https://developers.openai.com/api/reference/resources/audio/subresources/voice_consents/methods/create">here</a>.
 */
public class OpenAiCreateVoiceConsentRequest {

    /**
     * The BCP 47 language tag for the consent phrase (for example, fr-FR).
     */
    private final String language;

    /**
     * The label to use for this consent recording.
     */
    private final String name;

    /**
     * The consent audio recording file: the voice actor reading the consent phrase for the given language.
     * Maximum size is 10 MB.
     * Supported MIME types: audio/mpeg, audio/wav, audio/x-wav, audio/ogg, audio/aac, audio/flac, audio/webm,
     * audio/mp4.
     */
    private final AudioFile recording;

    public OpenAiCreateVoiceConsentRequest(Builder builder) {
        this.name = builder.name;
        this.language = builder.language;
        this.recording = builder.recording;
    }

    public String language() {
        return language;
    }

    public String name() {
        return name;
    }

    public AudioFile recording() {
        return recording;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private String language;
        private String name;
        private AudioFile recording;

        public Builder language(String language) {
            this.language = language;
            return this;
        }

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder recording(AudioFile recording) {
            this.recording = recording;
            return this;
        }

        public OpenAiCreateVoiceConsentRequest build() {
            return new OpenAiCreateVoiceConsentRequest(this);
        }
    }
}
