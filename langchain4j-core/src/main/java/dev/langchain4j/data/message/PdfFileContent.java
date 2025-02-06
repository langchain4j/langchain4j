package dev.langchain4j.data.message;

import dev.langchain4j.Experimental;
import dev.langchain4j.data.pdf.PdfFile;

import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.regex.Pattern;

import static dev.langchain4j.data.message.ContentType.PDF;
import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

@Experimental
public class PdfFileContent implements Content {
    
    private final PdfFile pdfFile;

    @Override
    public ContentType type() {
        return PDF;
    }

    /**
     * Create a new {@link PdfFileContent} from the given url.
     *
     * @param url the url of the PDF.
     */
    public PdfFileContent(URI url) {
        this.pdfFile = PdfFile.builder()
            .url(ensureNotNull(url, "url"))
            .build();
    }

    /**
     * Create a new {@link PdfFileContent} from the given url.
     *
     * @param url the url of the PDF.
     */
    public PdfFileContent(String url) {
        this(URI.create(url));
    }

    /**
     * Create a new {@link PdfFileContent} from the given base64 data and mime type.
     *
     * @param base64Data the base64 data of the PDF.
     * @param mimeType the mime type of the PDF.
     */
    public PdfFileContent(String base64Data, String mimeType) {
        this.pdfFile = PdfFile.builder()
            .base64Data(ensureNotBlank(base64Data, "base64data"))
            .build();
    }

    /**
     * Create a new {@link PdfFileContent} from the given PDF file.
     *
     * @param pdfFile the PDF.
     */
    public PdfFileContent(PdfFile pdfFile) {
        this.pdfFile = pdfFile;
    }

    /**
     * Get the {@code PdfFile}.
     * @return the {@code PdfFile}.
     */
    public PdfFile pdfFile() {
        return pdfFile;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PdfFileContent that = (PdfFileContent) o;
        return Objects.equals(this.pdfFile, that.pdfFile);
    }

    @Override
    public int hashCode() {
        return Objects.hash(pdfFile);
    }

    @Override
    public String toString() {
        return "PdfFileContent {" +
            " pdfFile = " + pdfFile +
            " }";
    }

    /**
     * Create a new {@link PdfFileContent} from the given url.
     *
     * @param url the url of the PDF.
     * @return the new {@link PdfFileContent}.
     */
    public static PdfFileContent from(URI url) {
        return new PdfFileContent(url);
    }

    /**
     * Create a new {@link PdfFileContent} from the given url.
     *
     * @param url the url of the PDF.
     * @return the new {@link PdfFileContent}.
     */
    public static PdfFileContent from(String url) throws URISyntaxException {
        if (isURLEncoded(url)) {
            return new PdfFileContent(url);
        }

        URI uri = new URI(null, url, null);
        return from(uri);
    }

    /**
     * Create a new {@link PdfFileContent} from the given base64 data and mime type.
     *
     * @param base64Data the base64 data of the PDF.
     * @param mimeType the mime type of the PDF.
     * @return the new {@link PdfFileContent}.
     */
    public static PdfFileContent from(String base64Data, String mimeType) {
        return new PdfFileContent(base64Data, mimeType);
    }

    /**
     * Create a new {@link PdfFileContent} from the given PDF.
     *
     * @param pdfFile the PDF.
     * @return the new {@link PdfFileContent}.
     */
    public static PdfFileContent from(PdfFile pdfFile) {
        return new PdfFileContent(pdfFile);
    }

    public static boolean isURLEncoded(String url) {
        Pattern encoderPattern = Pattern.compile("%[0-9A-Fa-f]{2}");
        if (!encoderPattern.matcher(url).find()) {
            return false;
        }
        String decodedURL = URLDecoder.decode(url, StandardCharsets.UTF_8);
        return !decodedURL.equals(url);
    }

}
