package dev.langchain4j.model.audio;

import dev.langchain4j.Experimental;
import dev.langchain4j.data.audio.Audio;

import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

/**
 * Response containing the audio generated from text.
 */
@Experimental
public class AudioSpeechResponse {

    private final Audio audio;

    /**
     * Creates a new response with the given audio.
     *
     * @param audio The generated audio
     */
    public AudioSpeechResponse(Audio audio) {
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
    public static AudioSpeechResponse from(Audio audio) {
        return new AudioSpeechResponse(audio);
    }
}
