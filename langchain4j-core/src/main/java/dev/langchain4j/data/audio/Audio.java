package dev.langchain4j.data.audio;

import static dev.langchain4j.internal.Utils.quoted;

import java.net.URI;
import java.util.Objects;

/**
 * Represents audio data that can be used with various AI model implementations.
 * This class supports multiple formats for storing audio:
 *
 * <ul>
 *   <li><b>URL:</b> A reference to audio data located at a specific URI</li>
 *   <li><b>Binary Data:</b> Raw binary audio data as a byte array, primarily used by
 *       implementations like Azure OpenAI</li>
 *   <li><b>Base64 Data:</b> Base64 encoded string representation of audio data, primarily
 *       used by implementations like OpenAI</li>
 * </ul>
 *
 * Different AI model implementations may require different audio data formats,
 * so this class provides flexibility to support various use cases.
 */
public class Audio {

    private final URI url;
    private final byte[] binaryData;
    private final String base64Data;
    private final String mimeType;

    /**
     * Create a new {@link Audio} from the Builder.
     *
     * @param builder the builder.
     */
    private Audio(Builder builder) {
        this.url = builder.url;
        this.binaryData = builder.binaryData;
        this.base64Data = builder.base64Data;
        this.mimeType = builder.mimeType;
    }

    /**
     * Create a new {@link Builder}.
     *
     * @return the new {@link Builder}.
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Get the url of the audio.
     *
     * @return the url of the audio, or null if not set.
     */
    public URI url() {
        return url;
    }

    /**
     * Get the raw binary data of the audio as a byte array.
     * This format is primarily used by implementations like Azure OpenAI that require
     * raw binary audio data for processing.
     *
     * @return the raw binary data of the audio as a byte array, or null if not set.
     */
    public byte[] binaryData() {
        return binaryData;
    }

    /**
     * Get the Base64 encoded string representation of the audio data.
     * This format is primarily used by implementations like OpenAI that accept
     * Base64 encoded audio data in API requests.
     *
     * @return the Base64 encoded string representation of the audio data, or null if not set.
     */
    public String base64Data() {
        return base64Data;
    }

    /**
     * Get the mime type of the audio.
     *
     * @return the mime type of the audio, or null if not set.
     */
    public String mimeType() {
        return mimeType;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Audio that = (Audio) o;
        return Objects.equals(this.url, that.url)
                && Objects.equals(this.binaryData, that.binaryData)
                && Objects.equals(this.base64Data, that.base64Data)
                && Objects.equals(this.mimeType, that.mimeType);
    }

    @Override
    public int hashCode() {
        return Objects.hash(url, binaryData, base64Data, mimeType);
    }

    @Override
    public String toString() {
        return "Audio {" + " url = "
                + quoted(url) + ", base64Data = "
                + quoted(base64Data) + ", mimeType = "
                + quoted(mimeType) + " }";
    }

    /**
     * Builder for {@link Audio}.
     */
    public static class Builder {

        private URI url;
        private byte[] binaryData;
        private String base64Data;
        private String mimeType;

        /**
         * Create a new {@link Builder}.
         */
        public Builder() {}

        /**
         * Set the url of the audio.
         *
         * @param url the url of the audio.
         * @return {@code this}
         */
        public Builder url(URI url) {
            this.url = url;
            return this;
        }

        /**
         * Set the url of the audio.
         *
         * @param url the url of the audio.
         * @return {@code this}
         */
        public Builder url(String url) {
            return url(URI.create(url));
        }

        /**
         * Set the raw binary data of the audio as a byte array.
         * This is the preferred format for implementations like Azure OpenAI that require
         * raw binary audio data for processing.
         *
         * @param binaryData the raw binary data of the audio as a byte array.
         * @return {@code this}
         */
        public Builder binaryData(byte[] binaryData) {
            this.binaryData = binaryData;
            return this;
        }

        /**
         * Set the Base64 encoded string representation of the audio data.
         * This is the preferred format for implementations like OpenAI that accept
         * Base64 encoded audio data in API requests.
         *
         * @param base64Data the Base64 encoded string representation of the audio.
         * @return {@code this}
         */
        public Builder base64Data(String base64Data) {
            this.base64Data = base64Data;
            return this;
        }

        /**
         * Set the mime type of the audio.
         *
         * @param mimeType the mime type of the audio.
         * @return {@code this}
         */
        public Builder mimeType(String mimeType) {
            this.mimeType = mimeType;
            return this;
        }

        /**
         * Build the {@link Audio}.
         *
         * @return the {@link Audio}.
         */
        public Audio build() {
            return new Audio(this);
        }
    }
}
