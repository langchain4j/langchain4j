package dev.langchain4j.model.audio;

import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

import dev.langchain4j.Experimental;
import dev.langchain4j.data.audio.Audio;

/**
 * Response containing speech audio generated from speech audio.
 */
@Experimental
public class SpeechToSpeechResponse {

    private final Audio audio;
    private final String transcript;

    public SpeechToSpeechResponse(Audio audio, String transcript) {
        this.audio = ensureNotNull(audio, "audio");
        this.transcript = transcript;
    }

    public Audio audio() {
        return audio;
    }

    public String transcript() {
        return transcript;
    }

    public static SpeechToSpeechResponse from(Audio audio) {
        return new SpeechToSpeechResponse(audio, null);
    }

    public static SpeechToSpeechResponse from(Audio audio, String transcript) {
        return new SpeechToSpeechResponse(audio, transcript);
    }
}
