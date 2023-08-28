package dev.langchain4j.data.document;

import java.util.List;
import java.util.Objects;

import static java.util.stream.Collectors.toList;

/**
 * Defines the interface for transforming a {@link Document}.
 * Implementations can perform a variety of tasks such as transforming, filtering, enriching, etc.
 */
public interface DocumentTransformer {

    /**
     * Transforms a provided document.
     *
     * @param document The document to be transformed.
     * @return The transformed document, or null if the document should be filtered out.
     */
    Document transform(Document document);

    /**
     * Transforms all the provided documents.
     *
     * @param documents A list of documents to be transformed.
     * @return A list of transformed documents. The length of this list may be shorter or longer than the original list. Returns an empty list if all documents were filtered out.
     */
    default List<Document> transformAll(List<Document> documents) {
        return documents.stream()
                .map(this::transform)
                .filter(Objects::nonNull)
                .collect(toList());
    }
}
