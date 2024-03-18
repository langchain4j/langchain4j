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
 * {@code EmbeddingStoreIngestor} is responsible for the ingestion of {@link Document}s into an {@link EmbeddingStore}.
 * It manages the entire pipeline process, from (optional) splitting the {@code Document}s into {@link TextSegment}s,
 * generating {@link Embedding}s for {@code TextSegment}s using a provided {@link EmbeddingModel}, to finally
 * storing these embeddings into an {@link EmbeddingStore}.
 * <br>
 * Optionally, it can also transform {@code Document}s using a {@link DocumentTransformer} before splitting them,
 * which can be useful if you want to clean/enrich/format your {@code Document}s.
 * <br>
 * Optionally, it can also transform {@code TextSegment}s using a {@link TextSegmentTransformer} after they have been split.
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
     *                               If none is specified, tries to load one via SPI ({@link DocumentSplitterFactory}).
     * @param textSegmentTransformer The {@link TextSegmentTransformer} to use. Optional.
     * @param embeddingModel         The {@link EmbeddingModel} to use. Mandatory.
     *                               If none is specified, tries to load one via SPI ({@link EmbeddingModelFactory}).
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
     * Ingests specified {@link Document} into specified {@link EmbeddingStore}.
     * Uses {@link DocumentSplitter} and {@link EmbeddingModel} found via SPIs
     * ({@link DocumentSplitterFactory} and {@link EmbeddingModelFactory}).
     * Please import the {@code langchain4j-easy-rag} module to make the default {@code DocumentSplitter}
     * and {@code EmbeddingModel} available via SPIs.
     */
    public static void ingest(Document document, EmbeddingStore<TextSegment> embeddingStore) {
        builder().embeddingStore(embeddingStore).build().ingest(document);
    }

    /**
     * Ingests specified {@link Document}s into specified {@link EmbeddingStore}.
     * Uses {@link DocumentSplitter} and {@link EmbeddingModel} found via SPIs
     * ({@link DocumentSplitterFactory} and {@link EmbeddingModelFactory}).
     * Please import the {@code langchain4j-easy-rag} module to make the default {@code DocumentSplitter}
     * and {@code EmbeddingModel} available via SPIs.
     */
    public static void ingest(List<Document> documents, EmbeddingStore<TextSegment> embeddingStore) {
        builder().embeddingStore(embeddingStore).build().ingest(documents);
    }

    /**
     * Ingests a single {@code Document} into an {@link EmbeddingStore} that was specified
     * during the creation of this {@code EmbeddingStoreIngestor}.
     *
     * @param document the document to ingest.
     */
    public void ingest(Document document) {
        ingest(singletonList(document));
    }

    /**
     * Ingests multiple {@code Document}s into an {@link EmbeddingStore} that was specified
     * during the creation of this {@code EmbeddingStoreIngestor}.
     *
     * @param documents the documents to ingest.
     */
    public void ingest(Document... documents) {
        ingest(asList(documents));
    }

    /**
     * Ingests multiple {@code Document}s into an {@link EmbeddingStore} that was specified
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
         * If none is specified, tries to load one via SPI ({@link DocumentSplitterFactory}).
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
         * If none is specified, tries to load one via SPI ({@link EmbeddingModelFactory}).
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
