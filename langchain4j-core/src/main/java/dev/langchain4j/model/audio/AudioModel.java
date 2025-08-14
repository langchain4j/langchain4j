package dev.langchain4j.model.audio;

import dev.langchain4j.data.audio.Audio;
import dev.langchain4j.model.output.Response;

/**
 * A model that can process audio inputs.
 */
public interface AudioModel {

    /**
     * Given an audio file, generates a transcription.
     *
     * @param request The transcription request containing the audio file and optional parameters
     * @return The generated transcription response
     */
    AudioTranscriptionResponse transcribe(AudioTranscriptionRequest request);

    /**
     * Convenience method for simple transcription needs.
     * Given an audio file, generates a transcription.
     *
     * @param audio The audio file to generate a transcription from
     * @return The generated transcription response
     * @deprecated This method will be removed in a future release. Use {@link #transcribe(AudioTranscriptionRequest)} instead.
     */
    @Deprecated
    default Response<String> transcribe(Audio audio) {
        AudioTranscriptionRequest request = AudioTranscriptionRequest.builder(audio).build();
        AudioTranscriptionResponse response = transcribe(request);
        return Response.from(response.text());
    }
}