package dev.langchain4j.model.audio;

import dev.langchain4j.Experimental;
import dev.langchain4j.internal.JacocoIgnoreCoverageGenerated;
import java.time.Duration;
import java.util.Objects;

/**
 * A transcribed audio word with timestamps.
 */
@Experimental
public class AudioTranscriptionWord {

    private final String word;
    private final Duration startTime;
    private final Duration endTime;

    public AudioTranscriptionWord(String word, Duration startTime, Duration endTime) {
        this.word = word;
        this.startTime = startTime;
        this.endTime = endTime;
    }

    public String word() {
        return word;
    }

    public Duration startTime() {
        return startTime;
    }

    public Duration endTime() {
        return endTime;
    }

    @Override
    @JacocoIgnoreCoverageGenerated
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        AudioTranscriptionWord that = (AudioTranscriptionWord) o;
        return Objects.equals(word, that.word)
                && Objects.equals(startTime, that.startTime)
                && Objects.equals(endTime, that.endTime);
    }

    @Override
    @JacocoIgnoreCoverageGenerated
    public int hashCode() {
        return Objects.hash(word, startTime, endTime);
    }

    @Override
    @JacocoIgnoreCoverageGenerated
    public String toString() {
        return "AudioTranscriptionWord {" + " word = "
                + word + ", startTime = "
                + startTime + ", endTime = "
                + endTime + " }";
    }
}
