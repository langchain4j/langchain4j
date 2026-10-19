package dev.langchain4j.store.embedding;

import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;
import static dev.langchain4j.spi.ServiceHelper.loadFactories;
import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static java.util.stream.Collectors.toList;

import dev.langchain4j.Experimental;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.document.DocumentTransformer;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.EmbeddingInputMapper;
import dev.langchain4j.data.segment.MappedSegment;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.data.segment.TextSegmentTransformer;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.request.EmbeddingInputType;
import dev.langchain4j.model.embedding.request.EmbeddingRequest;
import dev.langchain4j.model.embedding.response.EmbeddingResponse;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.spi.data.document.splitter.DocumentSplitterFactory;
import dev.langchain4j.spi.model.embedding.EmbeddingModelFactory;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
 * <br>
 * <br>
 * Optionally, the {@code EmbeddingStoreIngestor} can map each {@code TextSegment} to one or more
 * {@link MappedSegment}s using a {@link EmbeddingInputMapper}. Each mapped segment carries the text to
 * embed and the content to store in the {@link EmbeddingStore}. This can be useful when a single
 * segment should be represented by multiple embeddings (for example, a summary, a natural-language
 * explanation, or hypothetical questions) while the original content is returned during retrieval.
 * <br>
 * <br>
 * If the documents have already been split into {@code TextSegment}s, use
 * {@link #ingestSegments(List)} instead of {@link #ingest(List)}.
 */
public class EmbeddingStoreIngestor {

    private static final Logger log = LoggerFactory.getLogger(EmbeddingStoreIngestor.class);

    private final DocumentTransformer documentTransformer;
    private final DocumentSplitter documentSplitter;
    private final TextSegmentTransformer textSegmentTransformer;
    private final EmbeddingInputMapper embeddingInputMapper;
    private final EmbeddingModel embeddingModel;
    private final EmbeddingStore<TextSegment> embeddingStore;
    private final EmbeddingInputType embeddingInputType;

    private record MappedSegments(List<TextSegment> toEmbed, List<TextSegment> toStore) {}

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
    public EmbeddingStoreIngestor(
            DocumentTransformer documentTransformer,
            DocumentSplitter documentSplitter,
            TextSegmentTransformer textSegmentTransformer,
            EmbeddingModel embeddingModel,
            EmbeddingStore<TextSegment> embeddingStore) {
        this(documentTransformer, documentSplitter, textSegmentTransformer, null, embeddingModel, embeddingStore, null);
    }

    /**
     * Creates an instance of an {@code EmbeddingStoreIngestor}.
     *
     * @param documentTransformer    The {@link DocumentTransformer} to use. Optional.
     * @param documentSplitter       The {@link DocumentSplitter} to use. Optional.
     *                               If none is specified, it tries to load one through SPI (see {@link DocumentSplitterFactory}).
     * @param textSegmentTransformer The {@link TextSegmentTransformer} to use. Optional.
     * @param embeddingInputMapper   The {@link EmbeddingInputMapper} to use. Optional.
     * @param embeddingModel         The {@link EmbeddingModel} to use. Mandatory.
     *                               If none is specified, it tries to load one through SPI (see {@link EmbeddingModelFactory}).
     * @param embeddingStore         The {@link EmbeddingStore} to use. Mandatory.
     * @since 1.23.0
     */
    public EmbeddingStoreIngestor(
            DocumentTransformer documentTransformer,
            DocumentSplitter documentSplitter,
            TextSegmentTransformer textSegmentTransformer,
            EmbeddingInputMapper embeddingInputMapper,
            EmbeddingModel embeddingModel,
            EmbeddingStore<TextSegment> embeddingStore) {
        this(
                documentTransformer,
                documentSplitter,
                textSegmentTransformer,
                embeddingInputMapper,
                embeddingModel,
                embeddingStore,
                null);
    }

    private EmbeddingStoreIngestor(
            DocumentTransformer documentTransformer,
            DocumentSplitter documentSplitter,
            TextSegmentTransformer textSegmentTransformer,
            EmbeddingInputMapper embeddingInputMapper,
            EmbeddingModel embeddingModel,
            EmbeddingStore<TextSegment> embeddingStore,
            EmbeddingInputType embeddingInputType) {
        this.documentTransformer = documentTransformer;
        this.documentSplitter = getOrDefault(documentSplitter, EmbeddingStoreIngestor::loadDocumentSplitter);
        this.textSegmentTransformer = textSegmentTransformer;
        this.embeddingInputMapper = embeddingInputMapper;
        this.embeddingModel = ensureNotNull(
                getOrDefault(embeddingModel, EmbeddingStoreIngestor::loadEmbeddingModel), "embeddingModel");
        this.embeddingStore = ensureNotNull(embeddingStore, "embeddingStore");
        this.embeddingInputType = embeddingInputType;
    }

    private static DocumentSplitter loadDocumentSplitter() {
        Collection<DocumentSplitterFactory> factories = loadFactories(DocumentSplitterFactory.class);
        if (factories.size() > 1) {
            throw new RuntimeException("Conflict: multiple document splitters have been found in the classpath. "
                    + "Please explicitly specify the one you wish to use.");
        }

        for (DocumentSplitterFactory factory : factories) {
            DocumentSplitter documentSplitter = factory.create();
            log.debug("Loaded the following document splitter through SPI: {}", documentSplitter);
            return documentSplitter;
        }

        return null;
    }

    private static EmbeddingModel loadEmbeddingModel() {
        Collection<EmbeddingModelFactory> factories = loadFactories(EmbeddingModelFactory.class);
        if (factories.size() > 1) {
            throw new RuntimeException("Conflict: multiple embedding models have been found in the classpath. "
                    + "Please explicitly specify the one you wish to use.");
        }

        for (EmbeddingModelFactory factory : factories) {
            EmbeddingModel embeddingModel = factory.create();
            log.debug("Loaded the following embedding model through SPI: {}", embeddingModel);
            return embeddingModel;
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
     *
     * @return result including information related to ingestion process.
     */
    public static IngestionResult ingest(Document document, EmbeddingStore<TextSegment> embeddingStore) {
        return builder().embeddingStore(embeddingStore).build().ingest(document);
    }

    /**
     * Ingests specified {@link Document}s into a specified {@link EmbeddingStore}.
     * <br>
     * Uses {@link DocumentSplitter} and {@link EmbeddingModel} found through SPIs
     * (see {@link DocumentSplitterFactory} and {@link EmbeddingModelFactory}).
     * <br>
     * For the "Easy RAG", import {@code langchain4j-easy-rag} module,
     * which contains a {@code DocumentSplitterFactory} and {@code EmbeddingModelFactory} implementations.
     *
     * @return result including information related to ingestion process.
     */
    public static IngestionResult ingest(List<Document> documents, EmbeddingStore<TextSegment> embeddingStore) {
        return builder().embeddingStore(embeddingStore).build().ingest(documents);
    }

    /**
     * Ingests a specified document into an {@link EmbeddingStore} that was specified
     * during the creation of this {@code EmbeddingStoreIngestor}.
     *
     * @param document the document to ingest.
     * @return result including information related to ingestion process.
     */
    public IngestionResult ingest(Document document) {
        return ingest(singletonList(document));
    }

    /**
     * Ingests specified documents into an {@link EmbeddingStore} that was specified
     * during the creation of this {@code EmbeddingStoreIngestor}.
     *
     * @param documents the documents to ingest.
     * @return result including information related to ingestion process.
     */
    public IngestionResult ingest(Document... documents) {
        return ingest(asList(documents));
    }

    /**
     * Ingests specified documents into an {@link EmbeddingStore} that was specified
     * during the creation of this {@code EmbeddingStoreIngestor}.
     *
     * @param documents the documents to ingest.
     * @return result including information related to ingestion process.
     */
    public IngestionResult ingest(List<Document> documents) {

        log.debug("Starting to ingest {} documents", documents.size());

        if (documentTransformer != null) {
            documents = documentTransformer.transformAll(documents);
            log.debug("Documents were transformed into {} documents", documents.size());
        }
        List<TextSegment> segments;
        if (documentSplitter != null) {
            segments = documentSplitter.splitAll(documents);
            log.debug("Documents were split into {} text segments", segments.size());
        } else {
            segments = documents.stream().map(Document::toTextSegment).collect(toList());
        }
        return ingestSegments(segments);
    }

    /**
     * Ingests the given text segments into the {@link EmbeddingStore} configured for this ingestor.
     * <p>
     * The configured {@link TextSegmentTransformer}, if any, is applied to the given segments.
     * The configured {@link EmbeddingInputMapper}, if any, is applied before embedding; the original
     * segments are stored in the embedding store.
     *
     * @param segments the text segments to ingest.
     * @return result including information related to the ingestion process.
     * @since 1.23.0
     */
    public IngestionResult ingestSegments(List<TextSegment> segments) {

        if (textSegmentTransformer != null) {
            int segmentCountBeforeTransformation = segments.size();
            segments = textSegmentTransformer.transformAll(segments);
            log.debug(
                    "{} text segments were transformed into {} text segments",
                    segmentCountBeforeTransformation,
                    segments.size());
        }

        MappedSegments mappedSegments = mapSegments(segments);
        List<TextSegment> segmentsToEmbed = mappedSegments.toEmbed();
        List<TextSegment> segmentsToStore = mappedSegments.toStore();

        if (segmentsToEmbed.isEmpty()) {
            log.debug("No text segments to embed, nothing to embed");
            return new IngestionResult(null);
        }

        log.debug("Starting to embed {} text segments", segmentsToEmbed.size());
        Response<List<Embedding>> embeddingsResponse = embedSegments(segmentsToEmbed);
        log.debug("Finished embedding {} text segments", segmentsToEmbed.size());

        log.debug("Starting to store {} text segments into the embedding store", segmentsToStore.size());
        embeddingStore.addAll(embeddingsResponse.content(), segmentsToStore);
        log.debug("Finished storing {} text segments into the embedding store", segmentsToStore.size());

        return new IngestionResult(embeddingsResponse.tokenUsage());
    }

    /**
     * Ingests the given text segments into the {@link EmbeddingStore} configured for this ingestor.
     *
     * @param segments the text segments to ingest.
     * @return result including information related to the ingestion process.
     * @since 1.23.0
     */
    public IngestionResult ingestSegments(TextSegment... segments) {
        return ingestSegments(asList(segments));
    }

    private MappedSegments mapSegments(List<TextSegment> segments) {
        if (embeddingInputMapper == null) {
            return new MappedSegments(segments, segments);
        }

        List<MappedSegment> mappedSegments = embeddingInputMapper.mapAll(segments);

        List<TextSegment> toEmbed = new ArrayList<>(mappedSegments.size());
        List<TextSegment> toStore = new ArrayList<>(mappedSegments.size());
        for (MappedSegment mappedSegment : mappedSegments) {
            if (mappedSegment == null) {
                continue;
            }
            toEmbed.add(mappedSegment.embeddingInput());
            toStore.add(mappedSegment.storedSegment());
        }
        log.debug("{} text segments were mapped to {} embedding inputs", segments.size(), toEmbed.size());
        return new MappedSegments(toEmbed, toStore);
    }

    private Response<List<Embedding>> embedSegments(List<TextSegment> segments) {
        if (embeddingInputType == null) {
            return embeddingModel.embedAll(segments);
        }
        EmbeddingResponse response = embeddingModel.embed(EmbeddingRequest.builder()
                .textSegments(segments)
                .inputType(embeddingInputType)
                .build());
        return Response.from(response.embeddings(), response.metadata().tokenUsage());
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
        private EmbeddingInputMapper embeddingInputMapper;
        private EmbeddingModel embeddingModel;
        private EmbeddingStore<TextSegment> embeddingStore;
        private EmbeddingInputType embeddingInputType;

        /**
         * Creates a new EmbeddingStoreIngestor builder.
         */
        public Builder() {}

        /**
         * Embeds the text segments with the given {@link EmbeddingInputType} (typically
         * {@link EmbeddingInputType#DOCUMENT}), so providers that encode queries and documents differently can
         * produce a document-optimized embedding.
         * <p>
         * When left {@code null} (the default), no input type is sent. If set, the chosen {@link EmbeddingModel}
         * must {@link EmbeddingModel#supportedParameters() support} the input type parameter, otherwise ingestion
         * fails fast.
         *
         * @param embeddingInputType the input type to embed documents with.
         * @return {@code this}
         */
        public Builder embeddingInputType(EmbeddingInputType embeddingInputType) {
            this.embeddingInputType = embeddingInputType;
            return this;
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
         * Sets the embedding input mapper. Optional.
         * <p>
         * The mapping is applied after {@link TextSegmentTransformer} (if configured) and produces
         * zero or more {@link MappedSegment}s. The embedding input of each mapped segment is embedded,
         * and its stored segment is written to the embedding store. The stored segment may differ from
         * the embedding input.
         *
         * @param embeddingInputMapper the embedding input mapper.
         * @return {@code this}
         */
        @Experimental
        public Builder embeddingInputMapper(EmbeddingInputMapper embeddingInputMapper) {
            this.embeddingInputMapper = embeddingInputMapper;
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
                    embeddingInputMapper,
                    embeddingModel,
                    embeddingStore,
                    embeddingInputType);
        }
    }
}
