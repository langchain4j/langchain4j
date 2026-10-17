package dev.langchain4j.model.openai;

import dev.langchain4j.Experimental;

/**
 * The audio formats supported by the OpenAI speech API.
 *
 * @since 1.23.0
 */
@Experimental
public enum OpenAiTextToSpeechResponseFormat {
    MP3("mp3", "audio/mpeg"),
    OPUS("opus", "audio/opus"),
    AAC("aac", "audio/aac"),
    FLAC("flac", "audio/flac"),
    WAV("wav", "audio/wav"),
    PCM("pcm", "audio/pcm");

    private final String stringValue;
    private final String mimeType;

    OpenAiTextToSpeechResponseFormat(String stringValue, String mimeType) {
        this.stringValue = stringValue;
        this.mimeType = mimeType;
    }

    /**
     * @return the MIME type of audio in this format, e.g. {@code audio/mpeg} for {@link #MP3}
     */
    public String mimeType() {
        return mimeType;
    }

    @Override
    public String toString() {
        return stringValue;
    }
}
