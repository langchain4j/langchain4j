package dev.langchain4j.store.embedding;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.document.DocumentTransformer;
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
 * Optionally, it can also transform documents before splitting them, which can be useful if you want
 * to clean your data, format it differently, etc.
 */
public class EmbeddingStoreIngestor {

    private final DocumentTransformer transformer;
    private final DocumentSplitter splitter;
    private final EmbeddingModel embeddingModel;
    private final EmbeddingStore<TextSegment> embeddingStore;

    public EmbeddingStoreIngestor(DocumentTransformer transformer,
                                  DocumentSplitter splitter,
                                  EmbeddingModel embeddingModel,
                                  EmbeddingStore<TextSegment> embeddingStore) {
        this.transformer = transformer;
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
        if (transformer != null) {
            documents = transformer.transformAll(documents);
        }
        List<TextSegment> segments = splitter.splitAll(documents);
        List<Embedding> embeddings = embeddingModel.embedAll(segments);
        embeddingStore.addAll(embeddings, segments);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private DocumentTransformer transformer;
        private DocumentSplitter splitter;
        private EmbeddingModel embeddingModel;
        private EmbeddingStore<TextSegment> embeddingStore;

        public Builder transformer(DocumentTransformer transformer) {
            this.transformer = transformer;
            return this;
        }

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
            return new EmbeddingStoreIngestor(transformer, splitter, embeddingModel, embeddingStore);
        }
    }
}
