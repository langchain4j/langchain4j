package dev.langchain4j.data.image;

import java.net.URI;
import java.util.Objects;

import static dev.langchain4j.internal.Utils.quoted;

/**
 * Represents an image as a URL or as a Base64-encoded string.
 */
public final class Image {

    private final URI url;
    private final String base64Data;
    private final String mimeType;
    private final String revisedPrompt;

    /**
     * Create a new {@link Image} from the Builder.
     * @param builder the builder.
     */
    private Image(Builder builder) {
        this.url = builder.url;
        this.base64Data = builder.base64Data;
        this.mimeType = builder.mimeType;
        this.revisedPrompt = builder.revisedPrompt;
    }

    /**
     * Create a new {@link Builder}.
     * @return the new {@link Builder}.
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Get the url of the image.
     * @return the url of the image, or null if not set.
     */
    public URI url() {
        return url;
    }

    /**
     * Get the base64 data of the image.
     * @return the base64 data of the image, or null if not set.
     */
    public String base64Data() {
        return base64Data;
    }

    /**
     * Get the mime type of the image.
     * @return the mime type of the image, or null if not set.
     */
    public String mimeType() {
        return mimeType;
    }

    /**
     * Get the revised prompt of the image.
     * @return the revised prompt of the image, or null if not set.
     */
    public String revisedPrompt() {
        return revisedPrompt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Image that = (Image) o;
        return Objects.equals(this.url, that.url)
                && Objects.equals(this.base64Data, that.base64Data)
                && Objects.equals(this.mimeType, that.mimeType)
                && Objects.equals(this.revisedPrompt, that.revisedPrompt);
    }

    @Override
    public int hashCode() {
        return Objects.hash(url, base64Data, mimeType, revisedPrompt);
    }

    @Override
    public String toString() {
        return "Image {" +
                " url = " + quoted(url) +
                ", base64Data = " + quoted(base64Data) +
                ", mimeType = " + quoted(mimeType) +
                ", revisedPrompt = " + quoted(revisedPrompt) +
                " }";
    }

    /**
     * Builder for {@link Image}.
     */
    public static class Builder {

        private URI url;
        private String base64Data;
        private String mimeType;
        private String revisedPrompt;

        /**
         * Create a new {@link Builder}.
         */
        public Builder() {}

        /**
         * Set the url of the image.
         * @param url the url of the image.
         * @return {@code this}
         */
        public Builder url(URI url) {
            this.url = url;
            return this;
        }

        /**
         * Set the url of the image.
         * @param url the url of the image.
         * @return {@code this}
         */
        public Builder url(String url) {
            return url(URI.create(url));
        }

        /**
         * Set the base64 data of the image.
         * @param base64Data the base64 data of the image.
         * @return {@code this}
         */
        public Builder base64Data(String base64Data) {
            this.base64Data = base64Data;
            return this;
        }

        /**
         * Set the mime type of the image.
         * @param mimeType the mime type of the image.
         * @return {@code this}
         */
        public Builder mimeType(String mimeType) {
            this.mimeType = mimeType;
            return this;
        }

        /**
         * Set the revised prompt of the image.
         * @param revisedPrompt the revised prompt of the image.
         * @return {@code this}
         */
        public Builder revisedPrompt(String revisedPrompt) {
            this.revisedPrompt = revisedPrompt;
            return this;
        }

        /**
         * Build the {@link Image}.
         * @return the {@link Image}.
         */
        public Image build() {
            return new Image(this);
        }
    }
}
