package dev.langchain4j.model.openai;

import java.util.Arrays;

/**
 * The built-in voices of the OpenAI speech API.
 * To use a custom voice instead, see {@link OpenAiCustomVoice}.
 *
 * @since 1.23.0
 */
public enum OpenAiTextToSpeechVoiceName {
    ALLOY("alloy"),
    ASH("ash"),
    BALLAD("ballad"),
    CORAL("coral"),
    ECHO("echo"),
    FABLE("fable"),
    ONYX("onyx"),
    NOVA("nova"),
    SAGE("sage"),
    SHIMMER("shimmer"),
    VERSE("verse"),
    MARIN("marin"),
    CEDAR("cedar");

    private final String stringValue;

    OpenAiTextToSpeechVoiceName(String stringValue) {
        this.stringValue = stringValue;
    }

    /**
     * @param value the voice name, as used by the OpenAI speech API (e.g. {@code alloy})
     * @return the matching built-in voice
     * @throws IllegalArgumentException if there is no built-in voice with that name
     */
    public static OpenAiTextToSpeechVoiceName from(String value) {
        for (OpenAiTextToSpeechVoiceName voiceName : values()) {
            if (voiceName.stringValue.equals(value)) {
                return voiceName;
            }
        }
        throw new IllegalArgumentException("Unknown built-in voice '" + value + "'. Supported voices are "
                + Arrays.toString(values()) + ". To use a custom voice, see OpenAiCustomVoice.");
    }

    @Override
    public String toString() {
        return stringValue;
    }
}
