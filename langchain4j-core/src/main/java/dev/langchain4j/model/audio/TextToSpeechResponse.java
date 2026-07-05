package dev.langchain4j.model.audio;

import dev.langchain4j.Experimental;
import dev.langchain4j.data.audio.Audio;

import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

/**
 * Response containing the audio generated from text.
 */
@Experimental
public class TextToSpeechResponse {

    private final Audio audio;

    /**
     * Creates a new response with the given audio.
     *
     * @param audio The generated audio
     */
    public TextToSpeechResponse(Audio audio) {
        this.audio = ensureNotNull(audio, "audio");
    }

    /**
     * @return The generated audio
     */
    public Audio audio() {
        return audio;
    }

    /**
     * Creates a new response with the given audio.
     *
     * @param audio The generated audio
     * @return A new response
     */
    public static TextToSpeechResponse from(Audio audio) {
        return new TextToSpeechResponse(audio);
    }
}
