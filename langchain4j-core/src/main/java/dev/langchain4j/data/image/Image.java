package dev.langchain4j.data.image;

import static dev.langchain4j.internal.Utils.quoted;

import java.net.URI;
import java.util.Objects;

public final class Image {

    private URI url;
    private String base64;
    private String revisedPrompt;

    private Image(Builder builder) {
        this.url = builder.url;
        this.base64 = builder.base64;
        this.revisedPrompt = builder.revisedPrompt;
    }

    public static Builder builder() {
        return new Builder();
    }

    public URI url() {
        return url;
    }

    public String base64() {
        return base64;
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
            Objects.equals(base64, image.base64) &&
            Objects.equals(revisedPrompt, image.revisedPrompt)
        );
    }

    @Override
    public int hashCode() {
        return Objects.hash(url, revisedPrompt);
    }

    @Override
    public String toString() {
        return (
            "Image{" +
            " url=" +
            quoted(url.toString()) +
            ", base64=" +
            quoted(base64) +
            ", revisedPrompt=" +
            quoted(revisedPrompt) +
            '}'
        );
    }

    public static class Builder {

        private URI url;
        private String base64;
        private String revisedPrompt;

        public Builder url(URI url) {
            this.url = url;
            return this;
        }

        public Builder base64(String base64) {
            this.base64 = base64;
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
