package dev.langchain4j.model.audio;

import dev.langchain4j.data.audio.AudioFile;

public interface AudioModel {
    /**
     * Given an audio file, generates a transcription.
     * 
     * @param audioFile The audio file to generate a transcription from.
     * @return The generated transcription response.
     */
    Response<Transcription> transcribe(AudioFile audioFile);
}