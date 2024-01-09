package dev.langchain4j.data.image;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.Objects;

import static dev.langchain4j.internal.Utils.quoted;

public final class Image {

    private final URI url;
    private final String base64Data;
    private final String mimeType;
    private final String revisedPrompt;

    private Image(Builder builder) {
        this.url = builder.url;
        this.base64Data = builder.base64Data;
        this.mimeType = builder.mimeType;
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

    public String mimeType() {
        return mimeType;
    }

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

    public static Image fromPath(Path path) {
        try {
            byte[] allBytes = Files.readAllBytes(path);
            String base64 = Base64.getEncoder().encodeToString(allBytes);
            return Image.builder()
                .url(path.toUri())
                .base64Data(base64)
                .build();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static class Builder {

        private URI url;
        private String base64Data;
        private String mimeType;
        private String revisedPrompt;

        public Builder url(URI url) {
            this.url = url;
            return this;
        }

        public Builder url(String url) {
            return url(URI.create(url));
        }

        public Builder base64Data(String base64Data) {
            this.base64Data = base64Data;
            return this;
        }

        public Builder mimeType(String mimeType) {
            this.mimeType = mimeType;
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
