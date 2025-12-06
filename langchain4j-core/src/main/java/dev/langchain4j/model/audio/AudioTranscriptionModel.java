package dev.langchain4j.model.audio;

import static dev.langchain4j.model.ModelProvider.OTHER;

import dev.langchain4j.Experimental;
import dev.langchain4j.data.audio.Audio;
import dev.langchain4j.model.ModelProvider;

/**
 * A model that can transcribe audio into text.
 */
@Experimental
public interface AudioTranscriptionModel {

    /**
     * Given a transcription request file, generates a transcription.
     *
     * @param request The transcription request containing the audio file and optional parameters
     * @return The generated transcription response
     */
    AudioTranscriptionResponse transcribe(AudioTranscriptionRequest request);

    default AudioTranscriptionResponse transcribe(Audio audioMessage) {
        return transcribe(
                AudioTranscriptionRequest.builder().audio(audioMessage).build());
    }

    default ModelProvider provider() {
        return OTHER;
    }

    /**
     * Convenience method for simple transcription needs.
     * Given an audio file, generates a transcription.
     *
     * @param audio The audio file to generate a transcription from
     * @return The generated transcription as a plain string
     */
    default String transcribeToText(Audio audio) {
        AudioTranscriptionRequest request =
                AudioTranscriptionRequest.builder(audio).build();
        AudioTranscriptionResponse response = transcribe(request);
        return response.text();
    }
}
