package dev.langchain4j.data.audio;

import java.net.URI;
import java.util.Objects;

import static dev.langchain4j.internal.Utils.quoted;

/**
 * Represents an audio file as a URL.
 */
public final class AudioFile {
    private final URI url;

    /**
     * Create a new {@link AudioFile} from the Builder.
     * 
     * @param builder the builder.
     */
    private AudioFile(Builder builder) {
        this.url = builder.url;
    }

    /**
     * Get the url of the audio file.
     * 
     * @return the url of the audio file, or null if not set.
     */
    public URI url() {
        return url;
    }

    /**
     * Returns the hash code value for the {@link AudioFile} object.
     *
     * @return the hash code value for the object.
     */
    @Override
    public int hashCode() {
        return Objects.hash(url);
    }

    /**
     * Indicates whether some other object is "equal to" this one.
     *
     * @param obj the reference object with which to compare.
     * @return true if this object is the same as the obj argument; false otherwise.
     */
    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        AudioFile that = (AudioFile) o;
        
        return Objects.equals(this.url, that.url);
    }

    /**
     * Build the {@link AudioFile}.
     * 
     * @return the {@link AudioFile}.
     */
    public AudioFile build() {
        return new AudioFile(this);
    }
}