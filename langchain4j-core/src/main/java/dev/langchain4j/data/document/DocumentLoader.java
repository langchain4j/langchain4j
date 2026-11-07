package dev.langchain4j.data.document;

import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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
     * <p>Forwards the source Metadata to the parsed Document. If both define the same metadata key
     * with a different value, the source value is kept, the document value is discarded,
     * and a warning is logged. To control which value is kept, remove the key in your
     * {@link DocumentParser} before returning the {@link Document}.
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
        List<String> conflicts = describeConflicts(documentMetadata, sourceMetadata);
        if (conflicts.isEmpty()) {
            return;
        }
        log.warn(
                "Metadata keys set by both the document and the source, with different values: {}. "
                        + "Keeping the source values and discarding the document values. "
                        + "To control this, remove these keys in your DocumentParser "
                        + "before returning the Document.",
                "{" + String.join(", ", conflicts) + "}");
    }

    static List<String> describeConflicts(Metadata documentMetadata, Map<String, Object> sourceMetadata) {
        Map<String, Object> documentValues = documentMetadata.toMap();
        return sourceMetadata.entrySet().stream()
                .filter(sourceEntry -> documentValues.containsKey(sourceEntry.getKey())
                        && !Objects.equals(documentValues.get(sourceEntry.getKey()), sourceEntry.getValue()))
                .sorted(Map.Entry.comparingByKey())
                .map(sourceEntry -> describeConflict(
                        sourceEntry.getKey(), documentValues.get(sourceEntry.getKey()), sourceEntry.getValue()))
                .toList();
    }

    private static String describeConflict(String key, Object documentValue, Object sourceValue) {
        if (documentValue.toString().equals(sourceValue.toString())) {
            // the values differ only in type, so without it the two would be indistinguishable
            return "%s=(document=\"%s\" (%s), source=\"%s\" (%s))"
                    .formatted(
                            key,
                            documentValue,
                            documentValue.getClass().getSimpleName(),
                            sourceValue,
                            sourceValue.getClass().getSimpleName());
        }
        return "%s=(document=\"%s\", source=\"%s\")".formatted(key, documentValue, sourceValue);
    }
}
