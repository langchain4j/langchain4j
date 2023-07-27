package dev.langchain4j.data.document;

import java.util.ArrayList;
import java.util.List;

/**
 * Defines the interface for transforming a Document.
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
     * @return A list of transformed documents. The length of this list may be shorter or longer than the original list. Returns an empty list if all documents are filtered out.
     */
    default List<Document> transformAll(List<Document> documents) {
        List<Document> transformedDocuments = new ArrayList<>();
        documents.forEach(document -> {
            Document transformedDocument = transform(document);
            if (transformedDocument != null) {
                transformedDocuments.add(transformedDocument);
            }
        });
        return transformedDocuments;
    }
}
