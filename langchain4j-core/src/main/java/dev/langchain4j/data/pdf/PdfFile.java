package dev.langchain4j.data.pdf;

import dev.langchain4j.Experimental;

import java.net.URI;
import java.util.Objects;

import static dev.langchain4j.internal.Utils.quoted;

@Experimental
public class PdfFile {
    private final URI url;
    private final String base64Data;

    /**
     * Create a new {@link PdfFile} from the Builder.
     * @param builder the builder.
     */
    private PdfFile(Builder builder) {
        this.url = builder.url;
        this.base64Data = builder.base64Data;
    }

    /**
     * Create a new {@link Builder}.
     * @return the new {@link Builder}.
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Get the url of the PDF.
     * @return the url of the PDF, or null if not set.
     */
    public URI url() {
        return url;
    }

    /**
     * Get the base64 data of the rich format document.
     * @return the base64 data of the rich format document, or null if not set.
     */
    public String base64Data() {
        return base64Data;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PdfFile that = (PdfFile) o;
        return Objects.equals(this.url, that.url)
            && Objects.equals(this.base64Data, that.base64Data);
    }

    @Override
    public int hashCode() {
        return Objects.hash(url, base64Data);
    }

    @Override
    public String toString() {
        return "PdfFile {" +
            " url = " + quoted(url) +
            ", base64Data = " + quoted(base64Data) +
            " }";
    }

    /**
     * Builder for {@link PdfFile}.
     */
    public static class Builder {

        private URI url;
        private String base64Data;

        /**
         * Create a new {@link Builder}.
         */
        public Builder() {}

        /**
         * Set the url of the PDF document.
         * @param url the url of the PDF document.
         * @return {@code this}
         */
        public Builder url(URI url) {
            this.url = url;
            return this;
        }

        /**
         * Set the url of the PDF document.
         * @param url the url of the PDF document.
         * @return {@code this}
         */
        public Builder url(String url) {
            return url(URI.create(url));
        }

        /**
         * Set the base64 data of the PDF document.
         * @param base64Data the base64 data of the PDF document.
         * @return {@code this}
         */
        public Builder base64Data(String base64Data) {
            this.base64Data = base64Data;
            return this;
        }

        /**
         * Build the {@link PdfFile}.
         * @return the {@link PdfFile}.
         */
        public PdfFile build() {
            return new PdfFile(this);
        }
    }
}
