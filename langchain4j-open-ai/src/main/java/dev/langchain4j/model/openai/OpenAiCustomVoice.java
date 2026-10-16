package dev.langchain4j.model.openai;

import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;

import dev.langchain4j.Experimental;

/**
 * A custom voice, referenced by its ID (e.g. {@code voice_1234}).
 * Custom voices are created with {@link OpenAiVoiceModel}.
 * To use a built-in voice instead, see {@link OpenAiTextToSpeechVoiceName}.
 *
 * @since 1.23.0
 */
@Experimental
public final class OpenAiCustomVoice {

    private final String id;

    private OpenAiCustomVoice(String id) {
        this.id = ensureNotBlank(id, "id");
    }

    public static OpenAiCustomVoice from(String id) {
        return new OpenAiCustomVoice(id);
    }

    public String id() {
        return id;
    }

    @Override
    public boolean equals(Object another) {
        if (this == another) return true;
        return another instanceof OpenAiCustomVoice that && id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public String toString() {
        return "OpenAiCustomVoice{id=" + id + "}";
    }
}
