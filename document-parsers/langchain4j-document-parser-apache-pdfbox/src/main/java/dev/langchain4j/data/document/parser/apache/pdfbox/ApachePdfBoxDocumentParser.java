package dev.langchain4j.data.document.parser.apache.pdfbox;

import dev.langchain4j.data.document.BlankDocumentException;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentParser;
import dev.langchain4j.data.document.Metadata;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentInformation;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.io.Writer;

import static dev.langchain4j.internal.Exceptions.illegalArgument;
import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.Utils.isNullOrBlank;

/**
 * Parses a PDF file into a {@link Document} using the Apache PDFBox library.
 * <p>
 * Create a parser with the default settings using {@code new ApachePdfBoxDocumentParser()},
 * or configure it with {@link #builder()}:
 * <pre>{@code
 * DocumentParser parser = ApachePdfBoxDocumentParser.builder()
 *         .includeMetadata(true)
 *         .maxCharacters(20_000_000)
 *         .maxCharactersPerPage(200_000)
 *         .build();
 * }</pre>
 *
 * <h2>Maximum number of extracted characters</h2>
 * By default, the parser extracts at most 10,000,000 characters of text from a document, and reads at most
 * 100,000 characters from a single page:
 * <ul>
 *     <li>The limit per document counts the characters of the returned text,
 *     including the spaces and line breaks that PDFBox adds between words, lines and pages.</li>
 *     <li>The limit per page counts the characters of the page's text as PDFBox reads it,
 *     before it adds spaces and line breaks. Every character drawn on the page counts, also when it is drawn
 *     more than once at the same position.</li>
 * </ul>
 * If a document or a page contains more characters, {@link #parse(InputStream)} throws a {@link RuntimeException}
 * instead of returning a {@link Document}. The exception message names the limit that was passed.
 * Use {@link Builder#maxCharacters(Integer)} and {@link Builder#maxCharactersPerPage(Integer)} to set different
 * limits, or {@code -1} for no limit.
 * <p>
 * The limit per page exists because PDFBox keeps every character of a page in memory, with its position and font,
 * until the whole page is processed.
 * <p>
 * The limits bound the amount of extracted text. They do not bound all the memory that PDFBox needs to read a
 * document: some small, highly compressed PDF files can need a lot of memory before these limits apply.
 * An {@link OutOfMemoryError} in the JVM of your application can affect the whole application, even with a heap limit
 * ({@code -Xmx}). So if you parse documents from sources you do not trust, parse them in a separate process with its
 * own heap limit.
 */
public class ApachePdfBoxDocumentParser implements DocumentParser {

    private static final int DEFAULT_MAX_CHARACTERS = 10_000_000;
    private static final int DEFAULT_MAX_CHARACTERS_PER_PAGE = 100_000;
    private static final int NO_LIMIT = -1;

    private final boolean includeMetadata;
    private final int maxCharacters;
    private final int maxCharactersPerPage;

    /**
     * Creates a parser with the default settings: no metadata is added to the parsed document,
     * and the default limits on the number of extracted characters apply.
     */
    public ApachePdfBoxDocumentParser() {
        this(false);
    }

    /**
     * Creates a parser with the default limits on the number of extracted characters.
     *
     * @param includeMetadata whether to add the metadata of the PDF file (for example its author) to the parsed document
     */
    public ApachePdfBoxDocumentParser(boolean includeMetadata) {
        this(builder().includeMetadata(includeMetadata));
    }

    /**
     * Creates a parser with the options of the given builder.
     * To create a parser, use {@link #builder()} and {@link Builder#build()}.
     * This constructor is for subclasses, which can use it to set the options of the parser, for example:
     * <pre>{@code
     * class MyDocumentParser extends ApachePdfBoxDocumentParser {
     *
     *     MyDocumentParser() {
     *         super(ApachePdfBoxDocumentParser.builder().maxCharacters(-1));
     *     }
     * }
     * }</pre>
     *
     * @param builder the builder with the options of the parser
     * @throws IllegalArgumentException if the options are not valid, see {@link Builder#build()}
     * @since 1.11.14
     */
    protected ApachePdfBoxDocumentParser(Builder builder) {
        this.includeMetadata = getOrDefault(builder.includeMetadata, false);
        this.maxCharacters = ensureValidLimit(
                getOrDefault(builder.maxCharacters, DEFAULT_MAX_CHARACTERS), "maxCharacters");
        this.maxCharactersPerPage = ensureValidLimit(
                getOrDefault(builder.maxCharactersPerPage, DEFAULT_MAX_CHARACTERS_PER_PAGE), "maxCharactersPerPage");
    }

    private static int ensureValidLimit(int limit, String name) {
        if (limit <= 0 && limit != NO_LIMIT) {
            throw illegalArgument("%s must be a positive number, or -1 for no limit, but was: %s", name, limit);
        }
        return limit;
    }

    /**
     * Creates a builder for an {@link ApachePdfBoxDocumentParser}.
     * Every option is optional: options that are not set (or set to {@code null}) use their default values.
     *
     * @return a new builder
     * @since 1.11.14
     */
    public static Builder builder() {
        return new Builder();
    }

    @Override
    public Document parse(InputStream inputStream) {
        try (PDDocument pdfDocument = PDDocument.load(inputStream)) {
            LimitedWriter writer = new LimitedWriter(maxCharacters);
            new LimitedTextStripper(maxCharactersPerPage).writeText(pdfDocument, writer);
            String text = writer.toString();
            if (isNullOrBlank(text)) {
                throw new BlankDocumentException();
            }
            return includeMetadata
                    ? Document.from(text, toMetadata(pdfDocument))
                    : Document.from(text);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private Metadata toMetadata(PDDocument pdDocument) {
        PDDocumentInformation documentInformation = pdDocument.getDocumentInformation();
        Metadata metadata = new Metadata();
        for (String metadataKey : documentInformation.getMetadataKeys()) {
            String value = documentInformation.getCustomMetadataValue(metadataKey);
            if (value != null) metadata.put(metadataKey, value);
        }
        return metadata;
    }

    private static class LimitedTextStripper extends PDFTextStripper {

        private final int maxCharactersPerPage;
        private long charactersOnPage;

        private LimitedTextStripper(int maxCharactersPerPage) throws IOException {
            this.maxCharactersPerPage = maxCharactersPerPage;
        }

        @Override
        protected void startPage(PDPage page) throws IOException {
            charactersOnPage = 0;
            super.startPage(page);
        }

        @Override
        protected void processTextPosition(TextPosition text) {
            super.processTextPosition(text);
            charactersOnPage += Math.max(1, text.getUnicode().length());
            if (maxCharactersPerPage != NO_LIMIT && charactersOnPage > maxCharactersPerPage) {
                throw new RuntimeException("Page " + getCurrentPageNo() + " of the PDF document contains more than "
                        + maxCharactersPerPage + " characters, which is the maximum this parser extracts from a page. "
                        + "To parse it, set a higher limit with "
                        + "ApachePdfBoxDocumentParser.builder().maxCharactersPerPage(...), or -1 for no limit.");
            }
        }
    }

    private static class LimitedWriter extends Writer {

        private final int maxCharacters;
        private final StringBuilder text = new StringBuilder();

        private LimitedWriter(int maxCharacters) {
            this.maxCharacters = maxCharacters;
        }

        @Override
        public void write(int c) {
            ensureCapacityFor(1);
            text.append((char) c);
        }

        @Override
        public void write(char[] chars, int offset, int length) {
            ensureCapacityFor(length);
            text.append(chars, offset, length);
        }

        @Override
        public void write(String string, int offset, int length) {
            ensureCapacityFor(length);
            text.append(string, offset, offset + length);
        }

        private void ensureCapacityFor(int length) {
            if (maxCharacters != NO_LIMIT && (long) text.length() + length > maxCharacters) {
                throw new RuntimeException("The text extracted from the PDF document has more than " + maxCharacters
                        + " characters, which is the maximum this parser extracts. To parse it, set a higher limit "
                        + "with ApachePdfBoxDocumentParser.builder().maxCharacters(...), or -1 for no limit.");
            }
        }

        @Override
        public void flush() {}

        @Override
        public void close() {}

        @Override
        public String toString() {
            return text.toString();
        }
    }

    /**
     * Builder for {@link ApachePdfBoxDocumentParser}.
     * Options that are not set (or set to {@code null}) use their default values.
     *
     * @since 1.11.14
     */
    public static final class Builder {

        private @Nullable Boolean includeMetadata;
        private @Nullable Integer maxCharacters;
        private @Nullable Integer maxCharactersPerPage;

        private Builder() {}

        /**
         * Sets whether the metadata of the PDF file (for example its author or creation date)
         * is added to the parsed {@link Document}.
         *
         * @param includeMetadata whether to include the metadata; {@code null} means the default, {@code false}
         * @return this builder
         * @since 1.11.14
         */
        public Builder includeMetadata(@Nullable Boolean includeMetadata) {
            this.includeMetadata = includeMetadata;
            return this;
        }

        /**
         * Sets the maximum number of characters of text that the parser extracts from the whole document.
         * The count includes the spaces and line breaks that PDFBox adds between words, lines and pages.
         * If the text of a document has more characters, {@link ApachePdfBoxDocumentParser#parse(InputStream)}
         * throws a {@link RuntimeException}.
         *
         * @param maxCharacters the maximum number of characters, a positive number, or {@code -1} for no limit;
         *                      {@code null} means the default, 10,000,000
         * @return this builder
         * @since 1.11.14
         */
        public Builder maxCharacters(@Nullable Integer maxCharacters) {
            this.maxCharacters = maxCharacters;
            return this;
        }

        /**
         * Sets the maximum number of characters that the parser reads from a single page.
         * The count is the number of characters of the page's text as PDFBox reads it, before it adds spaces and
         * line breaks. Every character drawn on the page counts, also when it is drawn more than once at the same
         * position.
         * If a page contains more characters, {@link ApachePdfBoxDocumentParser#parse(InputStream)}
         * throws a {@link RuntimeException}.
         * <p>
         * PDFBox keeps every character of a page in memory until the whole page is processed,
         * so this limit bounds the memory needed for one page.
         * A page of dense text usually contains a few thousand characters.
         *
         * @param maxCharactersPerPage the maximum number of characters per page, a positive number,
         *                             or {@code -1} for no limit; {@code null} means the default, 100,000
         * @return this builder
         * @since 1.11.14
         */
        public Builder maxCharactersPerPage(@Nullable Integer maxCharactersPerPage) {
            this.maxCharactersPerPage = maxCharactersPerPage;
            return this;
        }

        /**
         * Creates the parser.
         *
         * @return a new {@link ApachePdfBoxDocumentParser}
         * @throws IllegalArgumentException if {@link #maxCharacters(Integer)} or
         *                                  {@link #maxCharactersPerPage(Integer)} is not a positive number or
         *                                  {@code -1}
         * @since 1.11.14
         */
        public ApachePdfBoxDocumentParser build() {
            return new ApachePdfBoxDocumentParser(this);
        }
    }
}
