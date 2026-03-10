package dev.langchain4j.model.openai;

import dev.langchain4j.Experimental;


@Experimental
public enum OpenAiAudioSpeechModelName {

    TTS_1("tts-1"),
    TTS_1_HD("tts-1-hd"),
    GPT_4_O_MINI_TTS("gpt-4o-mini-tts"),
    GPT_4_O_MINI_TTS_2025_12_15("gpt-4o-mini-tts-2025-12-15");

    private final String stringValue;

    OpenAiAudioSpeechModelName(String stringValue) {
        this.stringValue = stringValue;
    }

    @Override
    public String toString() {
        return stringValue;
    }
}
