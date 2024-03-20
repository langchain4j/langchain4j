package dev.langchain4j.store.embedding;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.document.DocumentTransformer;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.data.segment.TextSegmentTransformer;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.spi.data.document.splitter.DocumentSplitterFactory;
import dev.langchain4j.spi.model.embedding.EmbeddingModelFactory;

import java.util.Collection;
import java.util.List;

import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;
import static dev.langchain4j.spi.ServiceHelper.loadFactories;
import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static java.util.stream.Collectors.toList;

/**
 * The {@code EmbeddingStoreIngestor} represents an ingestion pipeline and is responsible
 * for ingesting {@link Document}s into an {@link EmbeddingStore}.
 * <br>
 * <br>
 * In the simplest configuration, {@code EmbeddingStoreIngestor} embeds provided documents
 * using a provided {@link EmbeddingModel} and stores them, along with their {@link Embedding}s
 * in an {@code EmbeddingStore}.
 * <br>
 * <br>
 * Optionally, the {@code EmbeddingStoreIngestor} can transform documents using a provided {@link DocumentTransformer}.
 * This can be useful if you want to clean, enrich, or format documents before embedding them.
 * <br>
 * <br>
 * Optionally, the {@code EmbeddingStoreIngestor} can split documents into {@link TextSegment}s
 * using a provided {@link DocumentSplitter}.
 * This can be useful if documents are big, and you want to split them into smaller segments to improve the quality
 * of similarity searches and reduce the size and cost of a prompt sent to the LLM.
 * <br>
 * <br>
 * Optionally, the {@code EmbeddingStoreIngestor} can transform {@code TextSegment}s using a {@link TextSegmentTransformer}.
 * This can be useful if you want to clean, enrich, or format {@code TextSegment}s before embedding them.
 * <br>
 * Including a document title or a short summary in each {@code TextSegment} is a common technique
 * to improve the quality of similarity searches.
 */
public class EmbeddingStoreIngestor {

    private final DocumentTransformer documentTransformer;
    private final DocumentSplitter documentSplitter;
    private final TextSegmentTransformer textSegmentTransformer;
    private final EmbeddingModel embeddingModel;
    private final EmbeddingStore<TextSegment> embeddingStore;

    /**
     * Creates an instance of an {@code EmbeddingStoreIngestor}.
     *
     * @param documentTransformer    The {@link DocumentTransformer} to use. Optional.
     * @param documentSplitter       The {@link DocumentSplitter} to use. Optional.
     *                               If none is specified, it tries to load one through SPI (see {@link DocumentSplitterFactory}).
     * @param textSegmentTransformer The {@link TextSegmentTransformer} to use. Optional.
     * @param embeddingModel         The {@link EmbeddingModel} to use. Mandatory.
     *                               If none is specified, it tries to load one through SPI (see {@link EmbeddingModelFactory}).
     * @param embeddingStore         The {@link EmbeddingStore} to use. Mandatory.
     */
    public EmbeddingStoreIngestor(DocumentTransformer documentTransformer,
                                  DocumentSplitter documentSplitter,
                                  TextSegmentTransformer textSegmentTransformer,
                                  EmbeddingModel embeddingModel,
                                  EmbeddingStore<TextSegment> embeddingStore) {
        this.documentTransformer = documentTransformer;
        this.documentSplitter = getOrDefault(documentSplitter, EmbeddingStoreIngestor::loadDocumentSplitter);
        this.textSegmentTransformer = textSegmentTransformer;
        this.embeddingModel = ensureNotNull(
                getOrDefault(embeddingModel, EmbeddingStoreIngestor::loadEmbeddingModel),
                "embeddingModel"
        );
        this.embeddingStore = ensureNotNull(embeddingStore, "embeddingStore");
    }

    private static DocumentSplitter loadDocumentSplitter() {
        Collection<DocumentSplitterFactory> factories = loadFactories(DocumentSplitterFactory.class);
        if (factories.size() > 1) {
            throw new RuntimeException("Conflict: multiple document splitters have been found in the classpath. " +
                    "Please explicitly specify the one you wish to use.");
        }

        for (DocumentSplitterFactory factory : factories) {
            return factory.create();
        }

        return null;
    }

    private static EmbeddingModel loadEmbeddingModel() {
        Collection<EmbeddingModelFactory> factories = loadFactories(EmbeddingModelFactory.class);
        if (factories.size() > 1) {
            throw new RuntimeException("Conflict: multiple embedding models have been found in the classpath. " +
                    "Please explicitly specify the one you wish to use.");
        }

        for (EmbeddingModelFactory factory : factories) {
            return factory.create();
        }

        return null;
    }

    /**
     * Ingests a specified {@link Document} into a specified {@link EmbeddingStore}.
     * <br>
     * Uses {@link DocumentSplitter} and {@link EmbeddingModel} found through SPIs
     * (see {@link DocumentSplitterFactory} and {@link EmbeddingModelFactory}).
     * <br>
     * For the "Easy RAG", import {@code langchain4j-easy-rag} module,
     * which contains a {@code DocumentSplitterFactory} and {@code EmbeddingModelFactory} implementations.
     */
    public static void ingest(Document document, EmbeddingStore<TextSegment> embeddingStore) {
        builder().embeddingStore(embeddingStore).build().ingest(document);
    }

    /**
     * Ingests specified {@link Document}s into a specified {@link EmbeddingStore}.
     * <br>
     * Uses {@link DocumentSplitter} and {@link EmbeddingModel} found through SPIs
     * (see {@link DocumentSplitterFactory} and {@link EmbeddingModelFactory}).
     * <br>
     * For the "Easy RAG", import {@code langchain4j-easy-rag} module,
     * which contains a {@code DocumentSplitterFactory} and {@code EmbeddingModelFactory} implementations.
     */
    public static void ingest(List<Document> documents, EmbeddingStore<TextSegment> embeddingStore) {
        builder().embeddingStore(embeddingStore).build().ingest(documents);
    }

    /**
     * Ingests a specified document into an {@link EmbeddingStore} that was specified
     * during the creation of this {@code EmbeddingStoreIngestor}.
     *
     * @param document the document to ingest.
     */
    public void ingest(Document document) {
        ingest(singletonList(document));
    }

    /**
     * Ingests specified documents into an {@link EmbeddingStore} that was specified
     * during the creation of this {@code EmbeddingStoreIngestor}.
     *
     * @param documents the documents to ingest.
     */
    public void ingest(Document... documents) {
        ingest(asList(documents));
    }

    /**
     * Ingests specified documents into an {@link EmbeddingStore} that was specified
     * during the creation of this {@code EmbeddingStoreIngestor}.
     *
     * @param documents the documents to ingest.
     */
    public void ingest(List<Document> documents) {
        if (documentTransformer != null) {
            documents = documentTransformer.transformAll(documents);
        }
        List<TextSegment> segments;
        if (documentSplitter != null) {
            segments = documentSplitter.splitAll(documents);
        } else {
            segments = documents.stream()
                    .map(Document::toTextSegment)
                    .collect(toList());
        }
        if (textSegmentTransformer != null) {
            segments = textSegmentTransformer.transformAll(segments);
        }
        List<Embedding> embeddings = embeddingModel.embedAll(segments).content();
        embeddingStore.addAll(embeddings, segments);
    }

    /**
     * Creates a new EmbeddingStoreIngestor builder.
     *
     * @return the builder.
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * EmbeddingStoreIngestor builder.
     */
    public static class Builder {

        private DocumentTransformer documentTransformer;
        private DocumentSplitter documentSplitter;
        private TextSegmentTransformer textSegmentTransformer;
        private EmbeddingModel embeddingModel;
        private EmbeddingStore<TextSegment> embeddingStore;

        /**
         * Creates a new EmbeddingStoreIngestor builder.
         */
        public Builder() {
        }

        /**
         * Sets the document transformer. Optional.
         *
         * @param documentTransformer the document transformer.
         * @return {@code this}
         */
        public Builder documentTransformer(DocumentTransformer documentTransformer) {
            this.documentTransformer = documentTransformer;
            return this;
        }

        /**
         * Sets the document splitter. Optional.
         * If none is specified, it tries to load one through SPI (see {@link DocumentSplitterFactory}).
         * <br>
         * {@code DocumentSplitters.recursive()} from main ({@code langchain4j}) module is a good starting point.
         *
         * @param documentSplitter the document splitter.
         * @return {@code this}
         */
        public Builder documentSplitter(DocumentSplitter documentSplitter) {
            this.documentSplitter = documentSplitter;
            return this;
        }

        /**
         * Sets the text segment transformer. Optional.
         *
         * @param textSegmentTransformer the text segment transformer.
         * @return {@code this}
         */
        public Builder textSegmentTransformer(TextSegmentTransformer textSegmentTransformer) {
            this.textSegmentTransformer = textSegmentTransformer;
            return this;
        }

        /**
         * Sets the embedding model. Mandatory.
         * If none is specified, it tries to load one through SPI (see {@link EmbeddingModelFactory}).
         *
         * @param embeddingModel the embedding model.
         * @return {@code this}
         */
        public Builder embeddingModel(EmbeddingModel embeddingModel) {
            this.embeddingModel = embeddingModel;
            return this;
        }

        /**
         * Sets the embedding store. Mandatory.
         *
         * @param embeddingStore the embedding store.
         * @return {@code this}
         */
        public Builder embeddingStore(EmbeddingStore<TextSegment> embeddingStore) {
            this.embeddingStore = embeddingStore;
            return this;
        }

        /**
         * Builds the EmbeddingStoreIngestor.
         *
         * @return the EmbeddingStoreIngestor.
         */
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
