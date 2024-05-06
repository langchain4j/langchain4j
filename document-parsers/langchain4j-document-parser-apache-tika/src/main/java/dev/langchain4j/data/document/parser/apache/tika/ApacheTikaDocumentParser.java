package dev.langchain4j.data.document.parser.apache.tika;

import dev.langchain4j.data.document.BlankDocumentException;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentParser;
import org.apache.tika.exception.ZeroByteFileException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.sax.BodyContentHandler;
import org.xml.sax.ContentHandler;

import java.io.InputStream;
import java.util.function.Supplier;

import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.Utils.isNullOrBlank;

/**
 * Parses files into {@link Document}s using Apache Tika library, automatically detecting the file format.
 * This parser supports various file formats, including PDF, DOC, PPT, XLS.
 * For detailed information on supported formats,
 * please refer to the <a href="https://tika.apache.org/2.9.1/formats.html">Apache Tika documentation</a>.
 */
public class ApacheTikaDocumentParser implements DocumentParser {

    private static final int NO_WRITE_LIMIT = -1;
    public static final Supplier<Parser> DEFAULT_PARSER_SUPPLIER = AutoDetectParser::new;
    public static final Supplier<Metadata> DEFAULT_METADATA_SUPPLIER = Metadata::new;
    public static final Supplier<ParseContext> DEFAULT_PARSE_CONTEXT_SUPPLIER = ParseContext::new;
    public static final Supplier<ContentHandler> DEFAULT_CONTENT_HANDLER_SUPPLIER = () -> new BodyContentHandler(NO_WRITE_LIMIT);

    private final Supplier<Parser> parserSupplier;
    private final Supplier<ContentHandler> contentHandlerSupplier;
    private final Supplier<Metadata> metadataSupplier;
    private final Supplier<ParseContext> parseContextSupplier;

    /**
     * Creates an instance of an {@code ApacheTikaDocumentParser} with the default Tika components.
     * It uses {@link AutoDetectParser}, {@link BodyContentHandler} without write limit,
     * empty {@link Metadata} and empty {@link ParseContext}.
     */
    public ApacheTikaDocumentParser() {
        this((Supplier<Parser>) null, null, null, null);
    }

    /**
     * Creates an instance of an {@code ApacheTikaDocumentParser} with the provided Tika components.
     * If some of the components are not provided ({@code null}, the defaults will be used.
     *
     * @param parser         Tika parser to use. Default: {@link AutoDetectParser}
     * @param contentHandler Tika content handler. Default: {@link BodyContentHandler} without write limit
     * @param metadata       Tika metadata. Default: empty {@link Metadata}
     * @param parseContext   Tika parse context. Default: empty {@link ParseContext}
     * @deprecated Use the constructor with suppliers for Tika components if you intend to use this parser for multiple files.
     */
    @Deprecated
    public ApacheTikaDocumentParser(Parser parser,
                                    ContentHandler contentHandler,
                                    Metadata metadata,
                                    ParseContext parseContext) {
        this(
                () -> getOrDefault(parser, DEFAULT_PARSER_SUPPLIER),
                () -> getOrDefault(contentHandler, DEFAULT_CONTENT_HANDLER_SUPPLIER),
                () -> getOrDefault(metadata, DEFAULT_METADATA_SUPPLIER),
                () -> getOrDefault(parseContext, DEFAULT_PARSE_CONTEXT_SUPPLIER)
        );
    }

    /**
     * Creates an instance of an {@code ApacheTikaDocumentParser} with the provided suppliers for Tika components.
     * If some of the suppliers are not provided ({@code null}), the defaults will be used.
     *
     * @param parserSupplier         Supplier for Tika parser to use. Default: {@link AutoDetectParser}
     * @param contentHandlerSupplier Supplier for Tika content handler. Default: {@link BodyContentHandler} without write limit
     * @param metadataSupplier       Supplier for Tika metadata. Default: empty {@link Metadata}
     * @param parseContextSupplier   Supplier for Tika parse context. Default: empty {@link ParseContext}
     */
    public ApacheTikaDocumentParser(Supplier<Parser> parserSupplier,
                                    Supplier<ContentHandler> contentHandlerSupplier,
                                    Supplier<Metadata> metadataSupplier,
                                    Supplier<ParseContext> parseContextSupplier) {
        this.parserSupplier = getOrDefault(parserSupplier, () -> DEFAULT_PARSER_SUPPLIER);
        this.contentHandlerSupplier = getOrDefault(contentHandlerSupplier, () -> DEFAULT_CONTENT_HANDLER_SUPPLIER);
        this.metadataSupplier = getOrDefault(metadataSupplier, () -> DEFAULT_METADATA_SUPPLIER);
        this.parseContextSupplier = getOrDefault(parseContextSupplier, () -> DEFAULT_PARSE_CONTEXT_SUPPLIER);
    }

    // TODO allow automatically extract metadata (e.g. creator, last-author, created/modified timestamp, etc)

    @Override
    public Document parse(InputStream inputStream) {
        try {
            Parser parser = parserSupplier.get();
            ContentHandler contentHandler = contentHandlerSupplier.get();
            Metadata metadata = metadataSupplier.get();
            ParseContext parseContext = parseContextSupplier.get();

            parser.parse(inputStream, contentHandler, metadata, parseContext);
            String text = contentHandler.toString();

            if (isNullOrBlank(text)) {
                throw new BlankDocumentException();
            }

            return Document.from(text);
        } catch (BlankDocumentException e) {
            throw e;
        } catch (ZeroByteFileException e) {
            throw new BlankDocumentException();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
