package dev.langchain4j.model.openai;

import dev.langchain4j.Experimental;

/**
 * @since 1.10.0
 */
@Experimental
public enum OpenAiAudioTranscriptionModelName {

    WHISPER_1("whisper-1"),
    GPT_4_O_TRANSCRIBE("gpt-4o-transcribe"),
    GPT_4_O_MINI_TRANSCRIBE("gpt-4o-mini-transcribe"),
    GPT_4_O_TRANSCRIBE_DIARIZE("gpt-4o-transcribe-diarize");

    private final String stringValue;

    OpenAiAudioTranscriptionModelName(String stringValue) {
        this.stringValue = stringValue;
    }

    @Override
    public String toString() {
        return stringValue;
    }
}
