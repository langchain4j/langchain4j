package dev.langchain4j.data.image;

import java.net.URI;
import java.util.Objects;

public final class Image {

    private URI url;
    private String revisedPrompt;

    public URI url() {
        return url;
    }

    public void url(URI url) {
        this.url = url;
    }

    private Image(Builder builder) {
        this.url = builder.url;
        this.revisedPrompt = builder.revisedPrompt;
    }

    public String revisedPrompt() {
        return revisedPrompt;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private URI url;
        private String revisedPrompt;

        public Builder url(URI url) {
            this.url = url;
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

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Image image = (Image) o;
        return Objects.equals(url, image.url) && Objects.equals(revisedPrompt, image.revisedPrompt);
    }

    @Override
    public int hashCode() {
        return Objects.hash(url, revisedPrompt);
    }

    @Override
    public String toString() {
        return "Image{" + "url='" + url + '\'' + ", revisedPrompt='" + revisedPrompt + '\'' + '}';
    }
}
