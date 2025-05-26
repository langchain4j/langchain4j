package dev.langchain4j.model.azure;

import dev.langchain4j.model.output.Response;

public interface AudioModel {
    /**
     * Given an audio file, generates a transcription.
     *
     * @param audioFile The audio file to generate a transcription from.
     * @return The generated transcription response.
     */
    Response<String> transcribe(Audio audioFile);
}
