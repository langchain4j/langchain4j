package dev.langchain4j.data.document.parser.apache.tika;

import static dev.langchain4j.internal.Exceptions.illegalArgument;
import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.Utils.isNullOrBlank;

import dev.langchain4j.data.document.BlankDocumentException;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentParser;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;
import org.apache.tika.exception.WriteLimitReachedException;
import org.apache.tika.exception.ZeroByteFileException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.sax.BodyContentHandler;
import org.jspecify.annotations.Nullable;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

/**
 * Parses an {@link InputStream} into a {@link Document} using the Apache Tika library by
 * automatically detecting the file format and extracting its textual content.
 * <p>
 * This parser supports a wide range of formats, including PDF, DOC, PPT, XLS, and many others.
 * Optionally, metadata can also be extracted and attached to the {@code Document}.
 * <p>
 * For a full list of supported formats, refer to the
 * <a href="https://tika.apache.org/3.0.0/formats.html">Apache Tika documentation</a>.
 * <p>
 * Create a parser with the default settings using {@code new ApacheTikaDocumentParser()},
 * or configure it with {@link #builder()}:
 * <pre>{@code
 * DocumentParser parser = ApacheTikaDocumentParser.builder()
 *         .includeMetadata(true)
 *         .maxCharacters(20_000_000)
 *         .build();
 * }</pre>
 *
 * <h2>Maximum number of extracted characters</h2>
 * By default, the parser extracts at most 10,000,000 characters of text from a document.
 * The count includes the line breaks and other whitespace that Tika adds to the text,
 * for example after each paragraph.
 * If a document contains more text, {@link #parse(InputStream)} throws a {@link RuntimeException}
 * instead of returning a {@link Document}. The exception message names the limit, and its cause is Tika's
 * {@link WriteLimitReachedException}.
 * Use {@link Builder#maxCharacters(Integer)} to set a different limit, or {@code -1} to extract all the text.
 * <p>
 * The limit applies to the default content handler. If you provide your own content handler with
 * {@link Builder#contentHandlerSupplier(Supplier)}, that handler decides how much text is collected,
 * for example {@code new BodyContentHandler(limit)}.
 *
 * <h2>Documents from untrusted sources</h2>
 * The limit bounds the amount of extracted text. It does not bound all the memory that Tika needs to read a document:
 * some small, highly compressed documents can need a lot of memory before any limit applies.
 * An {@link OutOfMemoryError} in the JVM of your application can affect the whole application, even with a heap limit
 * ({@code -Xmx}). Only a separate process with its own heap limit contains it.
 * <p>
 * So if you parse documents from sources you do not trust, parse them in a separate process with its own heap limit.
 * With Tika 3.x, Tika's {@code ForkParser} can do this: pass it with {@link Builder#parserSupplier(Supplier)}.
 * The separate process sends the extracted text back to your application, so give it a heap limit that is small
 * compared to the heap of your application. {@code ForkParser} starts a new Java process, so it needs a Java runtime
 * and does not work in an application compiled to a native image.
 */
public class ApacheTikaDocumentParser implements DocumentParser {

    private static final int DEFAULT_MAX_CHARACTERS = 10_000_000;
    private static final int NO_WRITE_LIMIT = -1;
    public static final Supplier<Parser> DEFAULT_PARSER_SUPPLIER = AutoDetectParser::new;
    public static final Supplier<Metadata> DEFAULT_METADATA_SUPPLIER = Metadata::new;
    public static final Supplier<ParseContext> DEFAULT_PARSE_CONTEXT_SUPPLIER = ParseContext::new;

    /**
     * Supplies a {@link BodyContentHandler} without a limit on the number of extracted characters.
     *
     * @deprecated The default content handler of this parser extracts at most 10,000,000 characters.
     * To extract all the text, use {@link Builder#maxCharacters(Integer)} with {@code -1}:
     * {@code ApacheTikaDocumentParser.builder().maxCharacters(-1).build()}.
     */
    @Deprecated(since = "1.11.14")
    public static final Supplier<ContentHandler> DEFAULT_CONTENT_HANDLER_SUPPLIER =
            () -> new BodyContentHandler(NO_WRITE_LIMIT);

    private final Supplier<Parser> parserSupplier;
    private final Supplier<ContentHandler> contentHandlerSupplier;
    private final Supplier<Metadata> metadataSupplier;
    private final Supplier<ParseContext> parseContextSupplier;
    private final int maxCharacters;

    private final boolean includeMetadata;

    /**
     * Creates an instance of an {@code ApacheTikaDocumentParser} with the default Tika components.
     * It uses {@link AutoDetectParser}, {@link BodyContentHandler} that extracts at most
     * 10,000,000 characters, empty {@link Metadata} and empty {@link ParseContext}.
     * Note: By default, no metadata is added to the parsed document.
     */
    public ApacheTikaDocumentParser() {
        this(false);
    }

    /**
     * Creates an instance of an {@code ApacheTikaDocumentParser} with the default Tika components.
     * It uses {@link AutoDetectParser}, {@link BodyContentHandler} that extracts at most
     * 10,000,000 characters, empty {@link Metadata} and empty {@link ParseContext}.
     *
     * @param includeMetadata        Whether to include metadata in the parsed document
     */
    public ApacheTikaDocumentParser(boolean includeMetadata) {
        this(null, null, null, null, includeMetadata);
    }

    /**
     * Creates an instance of an {@code ApacheTikaDocumentParser} with the provided Tika components.
     * If some of the components are not provided ({@code null}), the defaults will be used.
     *
     * @param parser         Tika parser to use. Default: {@link AutoDetectParser}
     * @param contentHandler Tika content handler. Default: {@link BodyContentHandler} that extracts at most
     *                       10,000,000 characters
     * @param metadata       Tika metadata. Default: empty {@link Metadata}
     * @param parseContext   Tika parse context. Default: empty {@link ParseContext}
     * @deprecated Use {@link #builder()} instead.
     */
    @Deprecated(forRemoval = true)
    public ApacheTikaDocumentParser(
            @Nullable Parser parser,
            @Nullable ContentHandler contentHandler,
            @Nullable Metadata metadata,
            @Nullable ParseContext parseContext) {
        this(
                parser == null ? null : () -> parser,
                contentHandler == null ? null : () -> contentHandler,
                metadata == null ? null : () -> metadata,
                parseContext == null ? null : () -> parseContext,
                false);
    }

    /**
     * Creates an instance of an {@code ApacheTikaDocumentParser} with the provided suppliers for Tika components.
     * If some of the suppliers are not provided ({@code null}), the defaults will be used.
     *
     * @param parserSupplier         Supplier for Tika parser to use. Default: {@link AutoDetectParser}
     * @param contentHandlerSupplier Supplier for Tika content handler. Default: {@link BodyContentHandler}
     *                               that extracts at most 10,000,000 characters
     * @param metadataSupplier       Supplier for Tika metadata. Default: empty {@link Metadata}
     * @param parseContextSupplier   Supplier for Tika parse context. Default: empty {@link ParseContext}
     * @deprecated Use {@link #builder()} instead.
     */
    @Deprecated(forRemoval = true)
    public ApacheTikaDocumentParser(
            @Nullable Supplier<Parser> parserSupplier,
            @Nullable Supplier<ContentHandler> contentHandlerSupplier,
            @Nullable Supplier<Metadata> metadataSupplier,
            @Nullable Supplier<ParseContext> parseContextSupplier) {
        this(parserSupplier, contentHandlerSupplier, metadataSupplier, parseContextSupplier, false);
    }

    /**
     * Creates an instance of an {@code ApacheTikaDocumentParser} with the provided suppliers for Tika components.
     * If some of the suppliers are not provided ({@code null}), the defaults will be used.
     * To also change the maximum number of extracted characters, use {@link #builder()}.
     *
     * @param parserSupplier         Supplier for Tika parser to use. Default: {@link AutoDetectParser}
     * @param contentHandlerSupplier Supplier for Tika content handler. Default: {@link BodyContentHandler}
     *                               that extracts at most 10,000,000 characters
     * @param metadataSupplier       Supplier for Tika metadata. Default: empty {@link Metadata}
     * @param parseContextSupplier   Supplier for Tika parse context. Default: empty {@link ParseContext}
     * @param includeMetadata        Whether to include metadata in the parsed document
     */
    public ApacheTikaDocumentParser(
            @Nullable Supplier<Parser> parserSupplier,
            @Nullable Supplier<ContentHandler> contentHandlerSupplier,
            @Nullable Supplier<Metadata> metadataSupplier,
            @Nullable Supplier<ParseContext> parseContextSupplier,
            boolean includeMetadata) {
        this(builder()
                .parserSupplier(parserSupplier)
                .contentHandlerSupplier(contentHandlerSupplier)
                .metadataSupplier(metadataSupplier)
                .parseContextSupplier(parseContextSupplier)
                .includeMetadata(includeMetadata));
    }

    /**
     * Creates a parser with the options of the given builder.
     * To create a parser, use {@link #builder()} and {@link Builder#build()}.
     * This constructor is for subclasses, which can use it to set the options of the parser, for example:
     * <pre>{@code
     * class MyDocumentParser extends ApacheTikaDocumentParser {
     *
     *     MyDocumentParser() {
     *         super(ApacheTikaDocumentParser.builder().maxCharacters(-1));
     *     }
     * }
     * }</pre>
     *
     * @param builder the builder with the options of the parser
     * @throws IllegalArgumentException if the options are not valid, see {@link Builder#build()}
     * @since 1.11.14
     */
    protected ApacheTikaDocumentParser(Builder builder) {
        if (builder.contentHandlerSupplier != null && builder.maxCharacters != null) {
            throw illegalArgument("maxCharacters applies only to the default content handler, "
                    + "so it cannot be used together with contentHandlerSupplier. "
                    + "Set the limit on your own content handler instead, for example new BodyContentHandler(limit).");
        }
        int maxCharacters = getOrDefault(builder.maxCharacters, DEFAULT_MAX_CHARACTERS);
        if (maxCharacters <= 0 && maxCharacters != NO_WRITE_LIMIT) {
            throw illegalArgument(
                    "maxCharacters must be a positive number, or -1 for no limit, but was: %s", maxCharacters);
        }
        this.maxCharacters = maxCharacters;
        this.parserSupplier = getOrDefault(builder.parserSupplier, () -> DEFAULT_PARSER_SUPPLIER);
        Supplier<ContentHandler> defaultContentHandlerSupplier = () -> new LimitedBodyContentHandler(maxCharacters);
        this.contentHandlerSupplier = getOrDefault(builder.contentHandlerSupplier, defaultContentHandlerSupplier);
        this.metadataSupplier = getOrDefault(builder.metadataSupplier, () -> DEFAULT_METADATA_SUPPLIER);
        this.parseContextSupplier = getOrDefault(builder.parseContextSupplier, () -> DEFAULT_PARSE_CONTEXT_SUPPLIER);
        this.includeMetadata = getOrDefault(builder.includeMetadata, false);
    }

    @Override
    public Document parse(InputStream inputStream) {
        ContentHandler contentHandler = null;
        try {
            Parser parser = parserSupplier.get();
            contentHandler = contentHandlerSupplier.get();
            Metadata metadata = metadataSupplier.get();
            ParseContext parseContext = parseContextSupplier.get();

            parser.parse(inputStream, contentHandler, metadata, parseContext);
            if (contentHandler instanceof LimitedBodyContentHandler limitedContentHandler) {
                limitedContentHandler.throwIfWriteLimitReached();
            }
            String text = contentHandler.toString();

            if (isNullOrBlank(text)) {
                throw new BlankDocumentException();
            }

            return includeMetadata ? Document.from(text, convert(metadata)) : Document.from(text);
        } catch (BlankDocumentException e) {
            throw e;
        } catch (ZeroByteFileException e) {
            throw new BlankDocumentException();
        } catch (Exception e) {
            if (contentHandler instanceof LimitedBodyContentHandler limitedContentHandler
                    && limitedContentHandler.writeLimitReached != null) {
                RuntimeException maxCharactersExceeded = new RuntimeException(
                        "The text extracted from the document has more than " + maxCharacters + " characters, "
                                + "which is the maximum this parser extracts. To parse it, set a higher limit with "
                                + "ApacheTikaDocumentParser.builder().maxCharacters(...), or -1 for no limit.",
                        limitedContentHandler.writeLimitReached);
                if (e != limitedContentHandler.writeLimitReached) {
                    maxCharactersExceeded.addSuppressed(e);
                }
                throw maxCharactersExceeded;
            }
            throw new RuntimeException(e);
        }
    }

    /**
     * Converts a Tika {@link Metadata} object into a {@link dev.langchain4j.data.document.Metadata} object.
     *
     *
     * @param tikaMetadata the {@code Metadata} object from the Tika library containing metadata information
     * @return a {@link dev.langchain4j.data.document.Metadata} object representing in langchain4j format.
     */
    private dev.langchain4j.data.document.Metadata convert(Metadata tikaMetadata) {

        final Map<String, String> tikaMetaData = new HashMap<>();

        for (String name : tikaMetadata.names()) {
            tikaMetaData.put(name, String.join(";", tikaMetadata.getValues(name)));
        }

        return new dev.langchain4j.data.document.Metadata(tikaMetaData);
    }

    private static final class LimitedBodyContentHandler extends BodyContentHandler {

        private @Nullable WriteLimitReachedException writeLimitReached;

        private LimitedBodyContentHandler(int maxCharacters) {
            super(maxCharacters);
        }

        @Override
        public void characters(char[] ch, int start, int length) throws SAXException {
            try {
                super.characters(ch, start, length);
            } catch (WriteLimitReachedException e) {
                writeLimitReached = e;
                throw e;
            }
        }

        @Override
        public void ignorableWhitespace(char[] ch, int start, int length) throws SAXException {
            try {
                super.ignorableWhitespace(ch, start, length);
            } catch (WriteLimitReachedException e) {
                writeLimitReached = e;
                throw e;
            }
        }

        private void throwIfWriteLimitReached() throws WriteLimitReachedException {
            if (writeLimitReached != null) {
                throw writeLimitReached;
            }
        }
    }

    /**
     * Creates a builder for an {@link ApacheTikaDocumentParser}.
     * Every option is optional: options that are not set (or set to {@code null}) use their default values.
     *
     * @return a new builder
     * @since 1.11.14
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for {@link ApacheTikaDocumentParser}.
     * Options that are not set (or set to {@code null}) use their default values.
     * <p>
     * Each supplier is called once for every parsed document, so the content handler and metadata suppliers should
     * return a new object on every call: these objects collect the text and the metadata of a single document.
     *
     * @since 1.11.14
     */
    public static final class Builder {

        private @Nullable Supplier<Parser> parserSupplier;
        private @Nullable Supplier<ContentHandler> contentHandlerSupplier;
        private @Nullable Supplier<Metadata> metadataSupplier;
        private @Nullable Supplier<ParseContext> parseContextSupplier;
        private @Nullable Boolean includeMetadata;
        private @Nullable Integer maxCharacters;

        private Builder() {}

        /**
         * Sets the supplier of the Tika {@link Parser} that reads the documents.
         * The supplier is called once for every parsed document.
         *
         * @param parserSupplier the supplier of the Tika parser;
         *                       {@code null} means the default, an {@link AutoDetectParser}
         * @return this builder
         * @since 1.11.14
         */
        public Builder parserSupplier(@Nullable Supplier<Parser> parserSupplier) {
            this.parserSupplier = parserSupplier;
            return this;
        }

        /**
         * Sets the supplier of the Tika {@link ContentHandler} that collects the text of the documents.
         * The supplier is called once for every parsed document, and the text of the document is the
         * {@code toString()} of the handler after parsing.
         * <p>
         * Your own content handler decides how much text is collected, so this option cannot be used together with
         * {@link #maxCharacters(Integer)}. To limit the text, set the limit on your handler,
         * for example {@code new BodyContentHandler(limit)}.
         *
         * @param contentHandlerSupplier the supplier of the content handler;
         *                               {@code null} means the default, a {@link BodyContentHandler} that extracts
         *                               at most {@link #maxCharacters(Integer)} characters
         * @return this builder
         * @since 1.11.14
         */
        public Builder contentHandlerSupplier(@Nullable Supplier<ContentHandler> contentHandlerSupplier) {
            this.contentHandlerSupplier = contentHandlerSupplier;
            return this;
        }

        /**
         * Sets the supplier of the Tika {@link Metadata} that is passed to the Tika parser.
         * The supplier is called once for every parsed document.
         *
         * @param metadataSupplier the supplier of the Tika metadata;
         *                         {@code null} means the default, an empty {@link Metadata}
         * @return this builder
         * @since 1.11.14
         */
        public Builder metadataSupplier(@Nullable Supplier<Metadata> metadataSupplier) {
            this.metadataSupplier = metadataSupplier;
            return this;
        }

        /**
         * Sets the supplier of the Tika {@link ParseContext} that is passed to the Tika parser.
         * The supplier is called once for every parsed document.
         *
         * @param parseContextSupplier the supplier of the Tika parse context;
         *                             {@code null} means the default, an empty {@link ParseContext}
         * @return this builder
         * @since 1.11.14
         */
        public Builder parseContextSupplier(@Nullable Supplier<ParseContext> parseContextSupplier) {
            this.parseContextSupplier = parseContextSupplier;
            return this;
        }

        /**
         * Sets whether the metadata that Tika extracts (for example the author or the content type)
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
         * Sets the maximum number of characters of text that the parser extracts from a document.
         * The count includes the line breaks and other whitespace that Tika adds to the text,
         * for example after each paragraph.
         * If a document contains more text, {@link ApacheTikaDocumentParser#parse(InputStream)} throws a
         * {@link RuntimeException} whose cause is Tika's {@link WriteLimitReachedException}.
         * <p>
         * This option applies to the default content handler, so it cannot be used together with
         * {@link #contentHandlerSupplier(Supplier)}.
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
         * Creates the parser.
         *
         * @return a new {@link ApacheTikaDocumentParser}
         * @throws IllegalArgumentException if {@link #maxCharacters(Integer)} is not a positive number or {@code -1},
         *                                  or if both {@link #maxCharacters(Integer)} and
         *                                  {@link #contentHandlerSupplier(Supplier)} are set
         * @since 1.11.14
         */
        public ApacheTikaDocumentParser build() {
            return new ApacheTikaDocumentParser(this);
        }
    }
}
