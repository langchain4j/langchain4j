package dev.langchain4j.model.audio;

import dev.langchain4j.Experimental;
import dev.langchain4j.internal.JacocoIgnoreCoverageGenerated;
import java.time.Duration;
import java.util.Objects;

/**
 * A transcribed audio segment with timestamps.
 */
@Experimental
public class AudioTranscriptionSegment {

    private final String text;
    private final Duration startTime;
    private final Duration endTime;

    public AudioTranscriptionSegment(String text, Duration startTime, Duration endTime) {
        this.text = text;
        this.startTime = startTime;
        this.endTime = endTime;
    }

    public String text() {
        return text;
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
        AudioTranscriptionSegment that = (AudioTranscriptionSegment) o;
        return Objects.equals(text, that.text)
                && Objects.equals(startTime, that.startTime)
                && Objects.equals(endTime, that.endTime);
    }

    @Override
    @JacocoIgnoreCoverageGenerated
    public int hashCode() {
        return Objects.hash(text, startTime, endTime);
    }

    @Override
    @JacocoIgnoreCoverageGenerated
    public String toString() {
        return "AudioTranscriptionSegment {" + " text = "
                + text + ", startTime = "
                + startTime + ", endTime = "
                + endTime + " }";
    }
}
