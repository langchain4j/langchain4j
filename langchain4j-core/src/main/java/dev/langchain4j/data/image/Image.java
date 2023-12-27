package dev.langchain4j.data.image;

import static dev.langchain4j.internal.Utils.quoted;

import java.net.URI;
import java.util.Objects;

public final class Image {

    private final URI url;
    private final String base64Data;
    private final String revisedPrompt;

    private Image(Builder builder) {
        this.url = builder.url;
        this.base64Data = builder.base64Data;
        this.revisedPrompt = builder.revisedPrompt;
    }

    public static Builder builder() {
        return new Builder();
    }

    public URI url() {
        return url;
    }

    public String base64Data() {
        return base64Data;
    }

    public String revisedPrompt() {
        return revisedPrompt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Image image = (Image) o;
        return (
            Objects.equals(url, image.url) &&
            Objects.equals(base64Data, image.base64Data) &&
            Objects.equals(revisedPrompt, image.revisedPrompt)
        );
    }

    @Override
    public int hashCode() {
        return Objects.hash(url, base64Data, revisedPrompt);
    }

    @Override
    public String toString() {
        return (
            "Image{" +
            " url=" +
            quoted(url.toString()) +
            ", base64Data=" +
            quoted(base64Data) +
            ", revisedPrompt=" +
            quoted(revisedPrompt) +
            '}'
        );
    }

    public static class Builder {

        private URI url;
        private String base64Data;
        private String revisedPrompt;

        public Builder url(URI url) {
            this.url = url;
            return this;
        }

        public Builder base64Data(String base64Data) {
            this.base64Data = base64Data;
            return this;
        }

        public Builder revisedPrompt(String revisedPrompt) {
            this.revisedPrompt = revisedPrompt;
            return this;
        }

        public Image build() {
            return new Image(this);
        }
    }
}
