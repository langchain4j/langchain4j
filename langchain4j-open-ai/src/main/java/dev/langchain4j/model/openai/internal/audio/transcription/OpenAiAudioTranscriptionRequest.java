package dev.langchain4j.model.openai.internal.audio.transcription;

/**
 * Represents the audio request.
 * Find description of parameters
 * <a href="https://platform.openai.com/docs/api-reference/audio/createTranscription">here</a>.
 */
public class OpenAiAudioTranscriptionRequest {

    /**
     * The audio file object (not file name) to transcribe,
     * in one of these formats: flac, mp3, mp4, mpeg, mpga, m4a, ogg, wav, or webm.
     */
    private final AudioFile file;

    /**
     * ID of the model to use. The options are
     * gpt-4o-transcribe, gpt-4o-mini-transcribe, whisper-1 (which is powered by our open source Whisper V2 model),
     * and gpt-4o-transcribe-diarize.
     */
    private final String model;

    private final String language;
    private final String prompt;
    private final Double temperature;

    public OpenAiAudioTranscriptionRequest(Builder builder) {
        this.file = builder.file;
        this.model = builder.model;
        this.language = builder.language;
        this.prompt = builder.prompt;
        this.temperature = builder.temperature;
    }

    public AudioFile file() {
        return file;
    }

    public String model() {
        return model;
    }

    public String language() {
        return language;
    }

    public String prompt() {
        return prompt;
    }

    public Double temperature() {
        return temperature;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private AudioFile file;
        private String model;
        private String language;
        private String prompt;
        private Double temperature;

        public Builder file(AudioFile file) {
            this.file = file;
            return this;
        }

        public Builder model(String model) {
            this.model = model;
            return this;
        }

        public Builder language(String language) {
            this.language = language;
            return this;
        }

        public Builder prompt(String prompt) {
            this.prompt = prompt;
            return this;
        }

        public Builder temperature(Double temperature) {
            this.temperature = temperature;
            return this;
        }

        public OpenAiAudioTranscriptionRequest build() {
            return new OpenAiAudioTranscriptionRequest(this);
        }
    }
}
