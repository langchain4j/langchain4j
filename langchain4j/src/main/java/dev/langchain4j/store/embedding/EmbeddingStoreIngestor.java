package dev.langchain4j.store.embedding;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;

import java.util.List;

import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;
import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;

/**
 * EmbeddingStoreIngestor is responsible for the ingestion of documents into an embedding store.
 * It manages the entire pipeline process, from splitting the documents into text segments,
 * generating embeddings for these segments using a provided embedding model, to finally
 * storing these embeddings into an embedding store.
 */
public class EmbeddingStoreIngestor {

    private final DocumentSplitter splitter;
    private final EmbeddingModel embeddingModel;
    private final EmbeddingStore<TextSegment> embeddingStore;

    public EmbeddingStoreIngestor(DocumentSplitter splitter,
                                  EmbeddingModel embeddingModel,
                                  EmbeddingStore<TextSegment> embeddingStore) {
        this.splitter = ensureNotNull(splitter, "splitter");
        this.embeddingModel = ensureNotNull(embeddingModel, "embeddingModel");
        this.embeddingStore = ensureNotNull(embeddingStore, "embeddingStore");
    }

    public void ingest(Document document) {
        ingest(singletonList(document));
    }

    public void ingest(Document... documents) {
        ingest(asList(documents));
    }

    public void ingest(List<Document> documents) {
        List<TextSegment> segments = splitter.split(documents);
        List<Embedding> embeddings = embeddingModel.embedAll(segments).get();
        embeddingStore.addAll(embeddings, segments);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private DocumentSplitter splitter;
        private EmbeddingModel embeddingModel;
        private EmbeddingStore<TextSegment> embeddingStore;

        public Builder splitter(DocumentSplitter splitter) {
            this.splitter = splitter;
            return this;
        }

        public Builder embeddingModel(EmbeddingModel embeddingModel) {
            this.embeddingModel = embeddingModel;
            return this;
        }

        public Builder embeddingStore(EmbeddingStore<TextSegment> embeddingStore) {
            this.embeddingStore = embeddingStore;
            return this;
        }

        public EmbeddingStoreIngestor build() {
            return new EmbeddingStoreIngestor(splitter, embeddingModel, embeddingStore);
        }
    }
}
