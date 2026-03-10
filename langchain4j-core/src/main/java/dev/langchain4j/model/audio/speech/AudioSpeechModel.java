package dev.langchain4j.model.audio.speech;

import static dev.langchain4j.model.ModelProvider.OTHER;

import dev.langchain4j.Experimental;
import dev.langchain4j.model.ModelProvider;

/**
 * A model that can generate audio from text.
 */
@Experimental
public interface AudioSpeechModel {

    /**
     * Given an input text, generates an audio speech.
     *
     * @param inputText The input text.
     * @return The generated speech response.
     */
    default byte[] generate(String inputText) {
        return generate(AudioSpeechRequest.builder().text(inputText).build());
    }

    /**
     * Given an audio speech request, generates an audio speech.
     *
     * @param request The transcription request containing the input text and optional parameters.
     * @return The generated speech response.
     */
    byte[] generate(AudioSpeechRequest request);

    default ModelProvider provider() {
        return OTHER;
    }
}
