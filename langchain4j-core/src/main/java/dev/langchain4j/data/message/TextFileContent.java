package dev.langchain4j.data.message;

import dev.langchain4j.Experimental;
import dev.langchain4j.data.text.TextFile;

import java.net.URI;
import java.util.Objects;

import static dev.langchain4j.data.message.ContentType.TEXT_FILE;
import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

@Experimental
public class TextFileContent implements Content {

    private final TextFile textFile;

    @Override
    public ContentType type() {
        return TEXT_FILE;
    }

    /**
     * Create a new {@link TextFileContent} from the given url.
     *
     * @param url the url of the text file.
     */
    public TextFileContent(URI url) {
        this.textFile = TextFile.builder()
            .url(ensureNotNull(url, "url"))
            .build();
    }

    /**
     * Create a new {@link TextFileContent} from the given url.
     *
     * @param url the url of the text file.
     */
    public TextFileContent(String url) {
        this(URI.create(url));
    }

    /**
     * Create a new {@link TextFileContent} from the given base64 data and mime type.
     *
     * @param base64Data the base64 data of the text file.
     * @param mimeType the mime type of the text file.
     */
    public TextFileContent(String base64Data, String mimeType) {
        this.textFile = TextFile.builder()
            .base64Data(ensureNotBlank(base64Data, "base64data"))
            .mimeType(mimeType)
            .build();
    }

    /**
     * Create a new {@link TextFileContent} from the given text file.
     *
     * @param textFile the text file.
     */
    public TextFileContent(TextFile textFile) {
        this.textFile = textFile;
    }

    /**
     * Get the {@code TextFile}.
     * @return the {@code TextFile}.
     */
    public TextFile textFile() {
        return textFile;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TextFileContent that = (TextFileContent) o;
        return Objects.equals(this.textFile, that.textFile);
    }

    @Override
    public int hashCode() {
        return Objects.hash(textFile);
    }

    @Override
    public String toString() {
        return "TextFileContent {" +
            " textFile = " + textFile +
            " }";
    }

    /**
     * Create a new {@link TextFileContent} from the given url.
     *
     * @param url the url of the text file.
     * @return the new {@link TextFileContent}.
     */
    public static TextFileContent from(URI url) {
        return new TextFileContent(url);
    }

    /**
     * Create a new {@link TextFileContent} from the given url.
     *
     * @param url the url of the text file.
     * @return the new {@link TextFileContent}.
     */
    public static TextFileContent from(String url) {
        return new TextFileContent(url);
    }

    /**
     * Create a new {@link TextFileContent} from the given base64 data and mime type.
     *
     * @param base64Data the base64 data of the text file.
     * @param mimeType the mime type of the text file.
     * @return the new {@link TextFileContent}.
     */
    public static TextFileContent from(String base64Data, String mimeType) {
        return new TextFileContent(base64Data, mimeType);
    }

    /**
     * Create a new {@link TextFileContent} from the given text file.
     *
     * @param textFile the text file.
     * @return the new {@link TextFileContent}.
     */
    public static TextFileContent from(TextFile textFile) {
        return new TextFileContent(textFile);
    }
}
