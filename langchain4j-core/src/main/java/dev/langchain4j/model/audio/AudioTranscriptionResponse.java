package dev.langchain4j.model.audio;

import dev.langchain4j.Experimental;

/**
 * Response containing the transcription of an audio file.
 */
@Experimental
public class AudioTranscriptionResponse {

    private final String text;

    /**
     * Creates a new response with the given text.
     *
     * @param text The transcribed text
     */
    public AudioTranscriptionResponse(String text) {
        this.text = text;
    }

    /**
     * @return The transcribed text
     */
    public String text() {
        return text;
    }

    /**
     * Creates a new response with the given text.
     *
     * @param text The transcribed text
     * @return A new response
     */
    public static AudioTranscriptionResponse from(String text) {
        return new AudioTranscriptionResponse(text);
    }
}
