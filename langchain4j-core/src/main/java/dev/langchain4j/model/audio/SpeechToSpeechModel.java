package dev.langchain4j.model.audio;

import static dev.langchain4j.model.ModelProvider.OTHER;

import dev.langchain4j.Experimental;
import dev.langchain4j.data.audio.Audio;
import dev.langchain4j.model.ModelProvider;

/**
 * A model that can generate speech audio directly from speech audio.
 */
@Experimental
public interface SpeechToSpeechModel {

    /**
     * Convenience method for simple speech-to-speech generation needs.
     *
     * @param audio The input audio.
     * @return The generated speech response.
     */
    default SpeechToSpeechResponse generate(Audio audio) {
        return generate(SpeechToSpeechRequest.builder().audio(audio).build());
    }

    /**
     * Generates speech audio from the given request.
     *
     * @param request The request containing input audio and optional parameters.
     * @return The generated speech response.
     */
    SpeechToSpeechResponse generate(SpeechToSpeechRequest request);

    default ModelProvider provider() {
        return OTHER;
    }
}
