package dev.langchain4j.model.audio;

import static dev.langchain4j.internal.Utils.copy;

import dev.langchain4j.Experimental;
import java.util.List;

/**
 * Response containing the transcription of an audio file.
 */
@Experimental
public class AudioTranscriptionResponse {

    private final String text;
    private final List<AudioTranscriptionSegment> segments;
    private final List<AudioTranscriptionWord> words;

    /**
     * Creates a new response with the given text.
     *
     * @param text The transcribed text
     */
    public AudioTranscriptionResponse(String text) {
        this(text, null, null);
    }

    /**
     * Creates a new response with the given text and timestamps.
     *
     * @param text The transcribed text
     * @param segments The transcribed segments with timestamps
     * @param words The transcribed words with timestamps
     */
    public AudioTranscriptionResponse(
            String text, List<AudioTranscriptionSegment> segments, List<AudioTranscriptionWord> words) {
        this.text = text;
        this.segments = copy(segments);
        this.words = copy(words);
    }

    /**
     * @return The transcribed text
     */
    public String text() {
        return text;
    }

    /**
     * @return The transcribed segments with timestamps
     * or an empty list when segment timestamps were not requested or not returned.
     */
    public List<AudioTranscriptionSegment> segments() {
        return segments;
    }

    /**
     * @return The transcribed words with timestamps
     * or an empty list when word timestamps were not requested or not returned.
     */
    public List<AudioTranscriptionWord> words() {
        return words;
    }

    /**
     * Creates a new response with the given text.
     *
     * @param text The transcribed text
     * @return A new response
     */
    public static AudioTranscriptionResponse from(String text) {
        return new AudioTranscriptionResponse(text);
    }
}
