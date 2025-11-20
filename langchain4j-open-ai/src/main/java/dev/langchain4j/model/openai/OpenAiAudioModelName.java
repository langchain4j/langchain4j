package dev.langchain4j.model.openai;

public enum OpenAiAudioModelName {
    WHISPER_1("whisper-1"),
    GPT_4_O_TRANSCRIBE("gpt-4o-transcribe"),
    GPT_4_O_MINI_TRANSCRIBE("gpt-4o-mini-transcribe"),
    GPT_4_O_TRANSCRIBE_DIARIZE("gpt-4o-transcribe-diarize");

    private final String stringValue;

    OpenAiAudioModelName(String stringValue) {
        this.stringValue = stringValue;
    }

    @Override
    public String toString() {
        return stringValue;
    }
}
