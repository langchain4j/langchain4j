package dev.langchain4j.store.embedding;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.document.DocumentTransformer;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.data.segment.TextSegmentTransformer;
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
 * Additionally, it can optionally transform segments after they have been split.
 */
public class EmbeddingStoreIngestor {

    private final DocumentTransformer documentTransformer;
    private final DocumentSplitter documentSplitter;
    private final TextSegmentTransformer textSegmentTransformer;
    private final EmbeddingModel embeddingModel;
    private final EmbeddingStore<TextSegment> embeddingStore;

    public EmbeddingStoreIngestor(DocumentTransformer documentTransformer,
                                  DocumentSplitter documentSplitter,
                                  TextSegmentTransformer textSegmentTransformer,
                                  EmbeddingModel embeddingModel,
                                  EmbeddingStore<TextSegment> embeddingStore) {
        this.documentTransformer = documentTransformer;
        this.documentSplitter = ensureNotNull(documentSplitter, "documentSplitter");
        this.textSegmentTransformer = textSegmentTransformer;
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
        if (documentTransformer != null) {
            documents = documentTransformer.transformAll(documents);
        }
        List<TextSegment> segments = documentSplitter.splitAll(documents);
        if (textSegmentTransformer != null) {
            segments = textSegmentTransformer.transformAll(segments);
        }
        List<Embedding> embeddings = embeddingModel.embedAll(segments).content();
        embeddingStore.addAll(embeddings, segments);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private DocumentTransformer documentTransformer;
        private DocumentSplitter documentSplitter;
        private TextSegmentTransformer textSegmentTransformer;
        private EmbeddingModel embeddingModel;
        private EmbeddingStore<TextSegment> embeddingStore;

        public Builder documentTransformer(DocumentTransformer documentTransformer) {
            this.documentTransformer = documentTransformer;
            return this;
        }

        public Builder documentSplitter(DocumentSplitter documentSplitter) {
            this.documentSplitter = documentSplitter;
            return this;
        }

        public Builder textSegmentTransformer(TextSegmentTransformer textSegmentTransformer) {
            this.textSegmentTransformer = textSegmentTransformer;
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
            return new EmbeddingStoreIngestor(
                    documentTransformer,
                    documentSplitter,
                    textSegmentTransformer,
                    embeddingModel,
                    embeddingStore
            );
        }
    }
}
