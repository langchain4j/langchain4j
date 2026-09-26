package dev.langchain4j.store.embedding;

import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;

import dev.langchain4j.data.document.Document;
import java.util.List;

/**
 * Defines the contract for ingesting {@link Document}s.
 *
 * @see EmbeddingStoreIngestor
 */
public interface StoreIngestor {

    /**
     * Ingests specified documents.
     *
     * @param documents the documents to ingest.
     * @return result including information related to the ingestion process.
     */
    IngestionResult ingest(List<Document> documents);

    /**
     * Ingests a specified document.
     *
     * @param document the document to ingest.
     * @return result including information related to the ingestion process.
     */
    default IngestionResult ingest(Document document) {
        return ingest(singletonList(document));
    }

    /**
     * Ingests specified documents.
     *
     * @param documents the documents to ingest.
     * @return result including information related to the ingestion process.
     */
    default IngestionResult ingest(Document... documents) {
        return ingest(asList(documents));
    }
}
