package dev.langchain4j.model.audio;

import static dev.langchain4j.model.ModelProvider.OTHER;

import dev.langchain4j.Experimental;
import dev.langchain4j.model.ModelProvider;

/**
 * A model that can synthesize audio from text.
 */
@Experimental
public interface TextToSpeechModel {

    /**
     * Convenience method for simple speech generation needs.
     * Given an input text, generates an audio speech.
     *
     * @param text The input text.
     * @return The generated speech response.
     */
    default TextToSpeechResponse synthesize(String text) {
        return synthesize(TextToSpeechRequest.builder().text(text).build());
    }

    /**
     * Given an audio speech request, generates an audio speech.
     *
     * @param request The speech request containing the input text and optional parameters.
     * @return The generated speech response.
     */
    TextToSpeechResponse synthesize(TextToSpeechRequest request);

    default ModelProvider provider() {
        return OTHER;
    }
}
