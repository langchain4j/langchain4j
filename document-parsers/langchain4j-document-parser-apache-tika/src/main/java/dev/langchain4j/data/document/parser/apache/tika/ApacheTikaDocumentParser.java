package dev.langchain4j.data.document.parser.apache.tika;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentParser;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.sax.BodyContentHandler;
import org.xml.sax.ContentHandler;

import java.io.InputStream;

import static dev.langchain4j.internal.Utils.getOrDefault;

/**
 * Parses files into {@link Document}s using Apache Tika library, automatically detecting the file format.
 * This parser supports various file formats, including PDF, DOC, PPT, XLS.
 * For detailed information on supported formats,
 * please refer to the <a href="https://tika.apache.org/2.9.1/formats.html">Apache Tika documentation</a>.
 */
public class ApacheTikaDocumentParser implements DocumentParser {

    private static final int NO_WRITE_LIMIT = -1;

    private final Parser parser;
    private final ContentHandler contentHandler;
    private final Metadata metadata;
    private final ParseContext parseContext;

    /**
     * Creates an instance of an {@code ApacheTikaDocumentParser} with the default Tika components.
     * It uses {@link AutoDetectParser}, {@link BodyContentHandler} without write limit,
     * empty {@link Metadata} and empty {@link ParseContext}.
     */
    public ApacheTikaDocumentParser() {
        this(null, null, null, null);
    }

    /**
     * Creates an instance of an {@code ApacheTikaDocumentParser} with the provided Tika components.
     * If some of the components are not provided ({@code null}, the defaults will be used.
     *
     * @param parser         Tika parser to use. Default: {@link AutoDetectParser}
     * @param contentHandler Tika content handler. Default: {@link BodyContentHandler} without write limit
     * @param metadata       Tika metadata. Default: empty {@link Metadata}
     * @param parseContext   Tika parse context. Default: empty {@link ParseContext}
     */
    public ApacheTikaDocumentParser(Parser parser,
                                    ContentHandler contentHandler,
                                    Metadata metadata,
                                    ParseContext parseContext) {
        this.parser = getOrDefault(parser, AutoDetectParser::new);
        this.contentHandler = getOrDefault(contentHandler, () -> new BodyContentHandler(NO_WRITE_LIMIT));
        this.metadata = getOrDefault(metadata, Metadata::new);
        this.parseContext = getOrDefault(parseContext, ParseContext::new);
    }

    // TODO allow automatically extract metadata (e.g. creator, last-author, created/modified timestamp, etc)

    @Override
    public Document parse(InputStream inputStream) {
        try {
            parser.parse(inputStream, contentHandler, metadata, parseContext);
            String text = contentHandler.toString();
            return Document.from(text);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
