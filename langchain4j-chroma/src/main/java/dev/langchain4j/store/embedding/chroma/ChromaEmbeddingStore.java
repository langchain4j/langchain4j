package dev.langchain4j.store.embedding.chroma;

import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.Utils.randomUUID;
import static dev.langchain4j.internal.ValidationUtils.ensureNotEmpty;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;
import static java.time.Duration.ofSeconds;
import static java.util.Collections.singletonList;
import static java.util.stream.Collectors.toList;

import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.filter.Filter;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.NotNull;

/**
 * Represents a store for embeddings using the Chroma backend.
 * Always uses cosine distance as the distance metric.
 */
public class ChromaEmbeddingStore implements EmbeddingStore<TextSegment> {

    private final ChromaClient chromaClient;
    private String collectionId;
    private final String collectionName;
    private final String tenantName;
    private final String databaseName;

    /**
     * Initializes a new instance of ChromaEmbeddingStore with the specified parameters. (V1 Deprecated)
     *
     * @param baseUrl        The base URL of the Chroma service.
     * @param collectionName The name of the collection in the Chroma service. If not specified, "default" will be used.
     * @param timeout        The timeout duration for the Chroma client. If not specified, 5 seconds will be used.
     * @param logRequests    If true, requests to the Chroma service are logged.
     * @param logResponses   If true, responses from the Chroma service are logged.
     */
    @Deprecated
    public ChromaEmbeddingStore(
            String baseUrl,
            String collectionName,
            Duration timeout,
            boolean logRequests,
            boolean logResponses) {
        this(
                baseUrl,
                "default_tenant",
                "default_database",
                collectionName,
                timeout,
                logRequests,
                logResponses
        );
    }

    /**
     * Initializes a new instance of ChromaEmbeddingStore with the specified parameters. (V2 API)
     *
     * @param baseUrl        The base URL of the Chroma service.
     * @param tenantName     The tenant of the collection in the Chroma service. If not specified, "default_tenant" will be used.
     * @param databaseName   The database of the collection in the Chroma service. If not specified, "default_database" will be used.
     * @param collectionName The name of the collection in the Chroma service. If not specified, "default" will be used.
     * @param timeout        The timeout duration for the Chroma client. If not specified, 5 seconds will be used.
     * @param logRequests    If true, requests to the Chroma service are logged.
     * @param logResponses   If true, responses from the Chroma service are logged.
     */
    public ChromaEmbeddingStore(
            String baseUrl,
            String tenantName,
            String databaseName,
            String collectionName,
            Duration timeout,
            boolean logRequests,
            boolean logResponses) {
        this.collectionName = getOrDefault(collectionName, "default");
        this.tenantName = getOrDefault(tenantName, "default_tenant");
        this.databaseName = getOrDefault(databaseName, "default_database");

        this.chromaClient = new ChromaClient.Builder()
                .baseUrl(baseUrl)
                .timeout(getOrDefault(timeout, ofSeconds(5)))
                .logRequests(logRequests)
                .logResponses(logResponses)
                .build();

        Collection collection = chromaClient.collection(this.tenantName, this.databaseName, this.collectionName);
        if (collection == null) {
            createCollection();
        } else {
            collectionId = collection.getId();
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private String baseUrl;
        private String collectionName;
        private String tenantName;
        private String databaseName;
        private Duration timeout;
        private boolean logRequests;
        private boolean logResponses;

        /**
         * @param baseUrl The base URL of the Chroma service.
         * @return builder
         */
        public Builder baseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
            return this;
        }

        /**
         * @param collectionName The name of the collection in the Chroma service. If not specified, "default" will be used.
         * @return builder
         */
        public Builder collectionName(String collectionName) {
            this.collectionName = collectionName;
            return this;
        }

        /**
         * @param tenantName The name of the tenant in the Chroma service. If not specified, "default" will be used.
         * @return builder
         */
        public Builder tenantName(String tenantName) {
            this.tenantName = tenantName;
            return this;
        }

        /**
         * @param databaseName The name of the database in the Chroma service. If not specified, "default" will be used.
         * @return builder
         */
        public Builder databaseName(String databaseName) {
            this.databaseName = databaseName;
            return this;
        }

        /**
         * @param timeout The timeout duration for the Chroma client. If not specified, 5 seconds will be used.
         * @return builder
         */
        public Builder timeout(Duration timeout) {
            this.timeout = timeout;
            return this;
        }

        public Builder logRequests(boolean logRequests) {
            this.logRequests = logRequests;
            return this;
        }

        public Builder logResponses(boolean logResponses) {
            this.logResponses = logResponses;
            return this;
        }

        public ChromaEmbeddingStore build() {
            return new ChromaEmbeddingStore(
                    this.baseUrl,
                    this.tenantName,
                    this.databaseName,
                    this.collectionName,
                    this.timeout,
                    this.logRequests,
                    this.logResponses);
        }
    }

    @Override
    public String add(Embedding embedding) {
        String id = randomUUID();
        add(id, embedding);
        return id;
    }

    @Override
    public void add(String id, Embedding embedding) {
        // for api V2, need to keep consistent number of IDs, embeddings, documents, URIs and metadatas
        // so can not just leave documents and metadatas null
        addInternal(id, embedding, new TextSegment("<empty>", new Metadata()));
    }

    @Override
    public String add(Embedding embedding, TextSegment textSegment) {
        String id = randomUUID();
        addInternal(id, embedding, textSegment);
        return id;
    }

    @Override
    public List<String> addAll(List<Embedding> embeddings) {
        List<String> ids = embeddings.stream().map(embedding -> randomUUID()).collect(toList());

        List<TextSegment> textSegments = new ArrayList<>();
        for (int i = 0; i < embeddings.size(); i++) {
            textSegments.add(new TextSegment("<empty>", new Metadata()));
        }
        addAll(ids, embeddings, textSegments);

        return ids;
    }

    private void addInternal(String id, Embedding embedding, TextSegment textSegment) {
        addAll(singletonList(id), singletonList(embedding), textSegment == null ? null : singletonList(textSegment));
    }

    @Override
    public void addAll(List<String> ids, List<Embedding> embeddings, List<TextSegment> textSegments) {
        AddEmbeddingsRequest addEmbeddingsRequest = AddEmbeddingsRequest.builder()
                .embeddings(embeddings.stream().map(Embedding::vector).collect(toList()))
                .ids(ids)
                .metadatas(
                        textSegments == null
                                ? null
                                : textSegments.stream()
                                        .map(TextSegment::metadata)
                                        .map(Metadata::toMap)
                                        .collect(toList()))
                .documents(
                        textSegments == null
                                ? null
                                : textSegments.stream().map(TextSegment::text).collect(toList()))
                .build();

        chromaClient.addEmbeddings(this.tenantName, this.databaseName, collectionId, addEmbeddingsRequest);
    }

    @Override
    public EmbeddingSearchResult<TextSegment> search(EmbeddingSearchRequest request) {
        QueryRequest queryRequest = new QueryRequest.Builder()
                .queryEmbeddings(request.queryEmbedding().vectorAsList())
                .nResults(request.maxResults())
                .where(ChromaMetadataFilterMapper.map(request.filter()))
                .build();

        return new EmbeddingSearchResult<>(queryAndFilter(queryRequest, request.minScore()));
    }

    @Override
    public void removeAll(java.util.Collection<String> ids) {
        ensureNotEmpty(ids, "ids");
        chromaClient.deleteEmbeddings(
                this.tenantName,
                this.databaseName,
                collectionId,
                DeleteEmbeddingsRequest.builder().ids(new ArrayList<>(ids)).build());
    }

    @Override
    public void removeAll(Filter filter) {
        ensureNotNull(filter, "filter");
        chromaClient.deleteEmbeddings(
                this.tenantName,
                this.databaseName,
                collectionId,
                DeleteEmbeddingsRequest.builder()
                        .where(ChromaMetadataFilterMapper.map(filter))
                        .build());
    }

    @Override
    public void removeAll() {
        chromaClient.deleteCollection(this.tenantName, this.databaseName, this.collectionName);
        createCollection();
    }

    private @NotNull List<EmbeddingMatch<TextSegment>> queryAndFilter(QueryRequest queryRequest, double minScore) {
        QueryResponse queryResponse =
                chromaClient.queryCollection(this.tenantName, this.databaseName, collectionId, queryRequest);
        List<EmbeddingMatch<TextSegment>> matches = toEmbeddingMatches(queryResponse);
        return matches.stream().filter(match -> match.score() >= minScore).collect(toList());
    }

    private static List<EmbeddingMatch<TextSegment>> toEmbeddingMatches(QueryResponse queryResponse) {
        List<EmbeddingMatch<TextSegment>> embeddingMatches = new ArrayList<>();

        for (int i = 0; i < queryResponse.getIds().get(0).size(); i++) {
            double score = distanceToScore(queryResponse.getDistances().get(0).get(i));
            String embeddingId = queryResponse.getIds().get(0).get(i);
            Embedding embedding =
                    Embedding.from(queryResponse.getEmbeddings().get(0).get(i));
            TextSegment textSegment = toTextSegment(queryResponse, i);

            embeddingMatches.add(new EmbeddingMatch<>(score, embeddingId, embedding, textSegment));
        }
        return embeddingMatches;
    }

    /**
     * By default, cosine distance will be used. For details: <a href="https://docs.trychroma.com/usage-guide"></a>
     * Converts a cosine distance in the range [0, 2] to a score in the range [0, 1].
     *
     * @param distance The distance value.
     * @return The converted score.
     */
    private static double distanceToScore(double distance) {
        return 1 - (distance / 2);
    }

    private static TextSegment toTextSegment(QueryResponse queryResponse, int i) {
        String text = queryResponse.getDocuments().get(0).get(i);
        Map<String, Object> metadata = queryResponse.getMetadatas().get(0).get(i);
        return text == null ? null : TextSegment.from(text, metadata == null ? new Metadata() : new Metadata(metadata));
    }

    private void createCollection() {
        collectionId = chromaClient
                .createCollection(this.tenantName, this.databaseName, new CreateCollectionRequest(this.collectionName))
                .getId();
    }
}
