package dev.langchain4j.data.document;

import java.io.InputStream;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Utility class for loading documents.
 */
public class DocumentLoader {

    private static final Logger log = LoggerFactory.getLogger(DocumentLoader.class);

    private DocumentLoader() {}

    /**
     * Loads a document from the given source using the given parser.
     *
     * <p>Forwards the source Metadata to the parsed Document. If both define the same metadata key,
     * the source value is kept, the document value is discarded, and a warning is logged.
     * To control which value is kept, remove the key in your {@link DocumentParser}
     * before returning the {@link Document}.
     *
     * @param source The source from which the document will be loaded.
     * @param parser The parser that will be used to parse the document.
     * @return The loaded document.
     * @throws BlankDocumentException when the parsed {@link Document} is blank/empty.
     */
    public static Document load(DocumentSource source, DocumentParser parser) {
        try (InputStream inputStream = source.inputStream()) {
            Document document = parser.parse(inputStream);
            Map<String, Object> sourceMetadata = source.metadata().toMap();
            warnAboutDiscardedValues(document.metadata(), sourceMetadata);
            document.metadata().putAll(sourceMetadata);
            return document;
        } catch (BlankDocumentException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Failed to load document", e);
        }
    }

    private static void warnAboutDiscardedValues(Metadata documentMetadata, Map<String, Object> sourceMetadata) {
        sourceMetadata.forEach((key, sourceValue) -> {
            if (documentMetadata.containsKey(key)) {
                log.warn(
                        "Metadata key \"{}\" is set both by the document (\"{}\") and by the source (\"{}\"). "
                                + "Keeping the source value and discarding the document value. "
                                + "To control this, remove the key in your DocumentParser "
                                + "before returning the Document.",
                        key,
                        documentMetadata.toMap().get(key),
                        sourceValue);
            }
        });
    }
}
