package dev.langchain4j.store.embedding.qdrant;

import static dev.langchain4j.internal.Utils.isNullOrEmpty;
import static dev.langchain4j.internal.Utils.randomUUID;
import static dev.langchain4j.internal.ValidationUtils.ensureNotEmpty;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;
import static io.qdrant.client.PointIdFactory.id;
import static io.qdrant.client.QueryFactory.fusion;
import static io.qdrant.client.QueryFactory.nearest;
import static io.qdrant.client.ValueFactory.value;
import static io.qdrant.client.VectorFactory.vector;
import static io.qdrant.client.VectorsFactory.namedVectors;
import static io.qdrant.client.VectorsFactory.vectors;
import static io.qdrant.client.WithPayloadSelectorFactory.enable;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static java.util.Comparator.comparingDouble;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;

import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.*;
import io.qdrant.client.QdrantClient;
import io.qdrant.client.QdrantGrpcClient;
import io.qdrant.client.WithVectorsSelectorFactory;
import io.qdrant.client.grpc.Common.Filter;
import io.qdrant.client.grpc.Common.PointId;
import io.qdrant.client.grpc.JsonWithInt.Value;
import io.qdrant.client.grpc.Points;
import io.qdrant.client.grpc.Points.DeletePoints;
import io.qdrant.client.grpc.Points.PointStruct;
import io.qdrant.client.grpc.Points.PointsSelector;
import io.qdrant.client.grpc.Points.ScoredPoint;
import io.qdrant.client.grpc.Points.SearchPoints;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import javax.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Represents a <a href="https://qdrant.tech/">Qdrant</a> collection as an
 * embedding store. With
 * support for storing {@link dev.langchain4j.data.document.Metadata}.
 *
 * <p>Supports both dense-only search ({@link SearchMode#VECTOR}) and hybrid
 * sparse+dense search fused with Reciprocal Rank Fusion ({@link SearchMode#HYBRID}).
 * In HYBRID mode the underlying Qdrant collection must be configured with named
 * vectors matching {@code denseVectorName} and {@code sparseVectorName}.
 */
public class QdrantEmbeddingStore implements EmbeddingStore<TextSegment> {
    private static final Logger log = LoggerFactory.getLogger(QdrantEmbeddingStore.class);

    static final String DEFAULT_DENSE_VECTOR_NAME = "dense";
    static final String DEFAULT_SPARSE_VECTOR_NAME = "sparse";

    private final QdrantClient client;
    private final String payloadTextKey;
    private final String collectionName;

    private final SearchMode searchMode;
    private final SparseEncoder sparseEncoder;
    private final String denseVectorName;
    private final String sparseVectorName;

    /**
     * @param collectionName The name of the Qdrant collection.
     * @param host           The host of the Qdrant instance.
     * @param port           The GRPC port of the Qdrant instance.
     * @param useTls         Whether to use TLS(HTTPS).
     * @param payloadTextKey The field name of the text segment in the Qdrant
     * payload.
     * @param apiKey         The Qdrant API key to authenticate with.
     */
    public QdrantEmbeddingStore(
            String collectionName,
            String host,
            int port,
            boolean useTls,
            String payloadTextKey,
            @Nullable String apiKey) {
        this(
                collectionName,
                host,
                port,
                useTls,
                payloadTextKey,
                apiKey,
                SearchMode.VECTOR,
                null,
                DEFAULT_DENSE_VECTOR_NAME,
                DEFAULT_SPARSE_VECTOR_NAME);
    }

    /**
     * @param collectionName    The name of the Qdrant collection.
     * @param host              The host of the Qdrant instance.
     * @param port              The GRPC port of the Qdrant instance.
     * @param useTls            Whether to use TLS(HTTPS).
     * @param payloadTextKey    The field name of the text segment in the Qdrant
     * payload.
     * @param apiKey            The Qdrant API key to authenticate with.
     * @param searchMode        {@link SearchMode#VECTOR} for dense-only search,
     * {@link SearchMode#HYBRID} for sparse+dense RRF fusion.
     * @param sparseEncoder     Required when {@code searchMode} is HYBRID; used
     * to encode text into a {@link SparseVector}.
     * @param denseVectorName   Named-vector key for the dense vector in HYBRID mode.
     * @param sparseVectorName  Named-vector key for the sparse vector in HYBRID mode.
     */
    public QdrantEmbeddingStore(
            String collectionName,
            String host,
            int port,
            boolean useTls,
            String payloadTextKey,
            @Nullable String apiKey,
            SearchMode searchMode,
            @Nullable SparseEncoder sparseEncoder,
            String denseVectorName,
            String sparseVectorName) {

        validateHybridConfig(searchMode, sparseEncoder);

        QdrantGrpcClient.Builder grpcClientBuilder = QdrantGrpcClient.newBuilder(host, port, useTls);

        if (apiKey != null) {
            grpcClientBuilder.withApiKey(apiKey);
        }

        this.client = new QdrantClient(grpcClientBuilder.build());
        this.collectionName = collectionName;
        this.payloadTextKey = payloadTextKey;
        this.searchMode = searchMode;
        this.sparseEncoder = sparseEncoder;
        this.denseVectorName = denseVectorName;
        this.sparseVectorName = sparseVectorName;
    }

    /**
     * @param client         A Qdrant client instance.
     * @param collectionName The name of the Qdrant collection.
     * @param payloadTextKey The field name of the text segment in the Qdrant
     * payload.
     */
    public QdrantEmbeddingStore(QdrantClient client, String collectionName, String payloadTextKey) {
        this(
                client,
                collectionName,
                payloadTextKey,
                SearchMode.VECTOR,
                null,
                DEFAULT_DENSE_VECTOR_NAME,
                DEFAULT_SPARSE_VECTOR_NAME);
    }

    /**
     * @param client            A Qdrant client instance.
     * @param collectionName    The name of the Qdrant collection.
     * @param payloadTextKey    The field name of the text segment in the Qdrant
     * payload.
     * @param searchMode        {@link SearchMode#VECTOR} for dense-only search,
     * {@link SearchMode#HYBRID} for sparse+dense RRF fusion.
     * @param sparseEncoder     Required when {@code searchMode} is HYBRID.
     * @param denseVectorName   Named-vector key for the dense vector in HYBRID mode.
     * @param sparseVectorName  Named-vector key for the sparse vector in HYBRID mode.
     */
    public QdrantEmbeddingStore(
            QdrantClient client,
            String collectionName,
            String payloadTextKey,
            SearchMode searchMode,
            @Nullable SparseEncoder sparseEncoder,
            String denseVectorName,
            String sparseVectorName) {

        validateHybridConfig(searchMode, sparseEncoder);

        this.client = client;
        this.collectionName = collectionName;
        this.payloadTextKey = payloadTextKey;
        this.searchMode = searchMode;
        this.sparseEncoder = sparseEncoder;
        this.denseVectorName = denseVectorName;
        this.sparseVectorName = sparseVectorName;
    }

    @Override
    public String add(Embedding embedding) {
        String id = randomUUID();
        add(id, embedding);
        return id;
    }

    @Override
    public void add(String id, Embedding embedding) {
        addInternal(id, embedding, null);
    }

    @Override
    public String add(Embedding embedding, TextSegment textSegment) {
        String id = randomUUID();
        addInternal(id, embedding, textSegment);
        return id;
    }

    @Override
    public List<String> addAll(List<Embedding> embeddings) {
        List<String> ids = embeddings.stream().map(ignored -> randomUUID()).toList();
        addAll(ids, embeddings, null);
        return ids;
    }

    private void addInternal(String id, Embedding embedding, TextSegment textSegment) {
        addAll(singletonList(id), singletonList(embedding), textSegment == null ? null : singletonList(textSegment));
    }

    @Override
    public void addAll(List<String> ids, List<Embedding> embeddings, List<TextSegment> textSegments)
            throws RuntimeException {
        if (isNullOrEmpty(ids) || isNullOrEmpty(embeddings)) {
            log.info("Empty embeddings - no ops");
            return;
        }
        try {
            List<PointStruct> points = new ArrayList<>(embeddings.size());

            for (int i = 0; i < embeddings.size(); i++) {
                String id = ids.get(i);
                PointId qdrantId = toPointId(id);
                Embedding embedding = embeddings.get(i);
                TextSegment textSegment = textSegments != null ? textSegments.get(i) : null;

                Points.Vectors pointVectors;
                if (searchMode == SearchMode.HYBRID) {
                    if (textSegment == null) {
                        throw new IllegalArgumentException(
                                "HYBRID mode requires textSegments (to build sparse vectors)");
                    }
                    SparseVector sv = sparseEncoder.encode(textSegment.text());
                    validateSparse(sv);
                    pointVectors = namedVectors(Map.of(
                            denseVectorName, vector(embedding.vector()),
                            sparseVectorName, vector(sv.values(), sv.indices())));
                } else {
                    pointVectors = vectors(embedding.vector());
                }

                PointStruct.Builder pointBuilder =
                        PointStruct.newBuilder().setId(qdrantId).setVectors(pointVectors);

                if (textSegment != null) {
                    Map<String, Object> metadata = textSegment.metadata().toMap();
                    Map<String, Value> payload = ValueMapFactory.valueMap(metadata);
                    payload.put(payloadTextKey, value(textSegment.text()));
                    pointBuilder.putAllPayload(payload);
                }

                points.add(pointBuilder.build());
            }

            client.upsertAsync(collectionName, points).get();
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void remove(String id) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("id cannot be null or blank");
        }
        removeAll(Collections.singleton(id));
    }

    @Override
    public void removeAll(Collection<String> ids) {
        ensureNotEmpty(ids, "ids");
        try {

            Points.PointsIdsList pointsIdsList = Points.PointsIdsList.newBuilder()
                    .addAllIds(ids.stream().map(QdrantEmbeddingStore::toPointId).collect(toList()))
                    .build();
            PointsSelector pointsSelector =
                    PointsSelector.newBuilder().setPoints(pointsIdsList).build();

            client.deleteAsync(DeletePoints.newBuilder()
                            .setCollectionName(collectionName)
                            .setPoints(pointsSelector)
                            .build())
                    .get();
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void removeAll(dev.langchain4j.store.embedding.filter.Filter filter) {
        ensureNotNull(filter, "filter");
        try {
            Filter qdrantFilter = QdrantFilterConverter.convertExpression(filter);
            PointsSelector pointsSelector =
                    PointsSelector.newBuilder().setFilter(qdrantFilter).build();

            client.deleteAsync(DeletePoints.newBuilder()
                            .setCollectionName(collectionName)
                            .setPoints(pointsSelector)
                            .build())
                    .get();
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void removeAll() {
        clearStore();
    }

    @Override
    public EmbeddingSearchResult<TextSegment> search(EmbeddingSearchRequest request) {
        return switch (searchMode) {
            case VECTOR -> searchVectorOnly(request);
            case HYBRID -> searchHybrid(request);
        };
    }

    private EmbeddingSearchResult<TextSegment> searchVectorOnly(EmbeddingSearchRequest request) {
        SearchPoints.Builder searchBuilder = SearchPoints.newBuilder()
                .setCollectionName(collectionName)
                .addAllVector(request.queryEmbedding().vectorAsList())
                .setWithVectors(WithVectorsSelectorFactory.enable(true))
                .setWithPayload(enable(true))
                .setLimit(request.maxResults());

        if (request.filter() != null) {
            Filter filter = QdrantFilterConverter.convertExpression(request.filter());
            searchBuilder.setFilter(filter);
        }

        List<ScoredPoint> results;
        try {
            results = client.searchAsync(searchBuilder.build()).get();
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException(e);
        }

        return toSearchResult(results, request);
    }

    private EmbeddingSearchResult<TextSegment> searchHybrid(EmbeddingSearchRequest request) {
        String queryText = request.query();
        if (queryText == null || queryText.isBlank()) {
            throw new IllegalArgumentException("HYBRID search requires a non-blank query text");
        }

        SparseVector sv = sparseEncoder.encode(queryText);
        validateSparse(sv);

        List<Float> dv = request.queryEmbedding().vectorAsList();
        int limit = request.maxResults();
        int prefetchLimit = Math.max(40, limit * 8);

        // Converted once and applied to both prefetches so candidates are filtered
        // before RRF fusion, not only after.
        Filter qdrantFilter =
                request.filter() != null ? QdrantFilterConverter.convertExpression(request.filter()) : null;

        Points.PrefetchQuery.Builder sparsePrefetch = Points.PrefetchQuery.newBuilder()
                .setQuery(nearest(sv.values(), sv.indices()))
                .setUsing(sparseVectorName)
                .setLimit(prefetchLimit);
        if (qdrantFilter != null) sparsePrefetch.setFilter(qdrantFilter);

        Points.PrefetchQuery.Builder densePrefetch = Points.PrefetchQuery.newBuilder()
                .setQuery(nearest(dv))
                .setUsing(denseVectorName)
                .setLimit(prefetchLimit);
        if (qdrantFilter != null) densePrefetch.setFilter(qdrantFilter);

        Points.QueryPoints.Builder query = Points.QueryPoints.newBuilder()
                .setCollectionName(collectionName)
                .addPrefetch(sparsePrefetch.build())
                .addPrefetch(densePrefetch.build())
                .setQuery(fusion(Points.Fusion.RRF))
                .setLimit(limit)
                .setWithPayload(enable(true))
                .setWithVectors(WithVectorsSelectorFactory.enable(true));
        if (qdrantFilter != null) query.setFilter(qdrantFilter);

        List<ScoredPoint> results;
        try {
            results = client.queryAsync(query.build()).get();
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException(e);
        }

        return toSearchResult(results, request);
    }

    private EmbeddingSearchResult<TextSegment> toSearchResult(
            List<ScoredPoint> results, EmbeddingSearchRequest request) {
        if (results.isEmpty()) {
            return new EmbeddingSearchResult<>(emptyList());
        }

        List<EmbeddingMatch<TextSegment>> matches = results.stream()
                .map(p -> toEmbeddingMatch(p, request.queryEmbedding()))
                .filter(match -> match.score() >= request.minScore())
                .sorted(comparingDouble(EmbeddingMatch::score))
                .collect(toList());

        Collections.reverse(matches);
        return new EmbeddingSearchResult<>(matches);
    }

    /** Deletes all points from the Qdrant collection. */
    public void clearStore() {
        try {

            Filter emptyFilter = Filter.newBuilder().build();
            PointsSelector allPointsSelector =
                    PointsSelector.newBuilder().setFilter(emptyFilter).build();

            client.deleteAsync(DeletePoints.newBuilder()
                            .setCollectionName(collectionName)
                            .setPoints(allPointsSelector)
                            .build())
                    .get();
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException(e);
        }
    }

    /** Closes the underlying GRPC client. */
    public void close() {
        client.close();
    }

    private EmbeddingMatch<TextSegment> toEmbeddingMatch(ScoredPoint scoredPoint, Embedding referenceEmbedding) {
        Map<String, Value> payload = scoredPoint.getPayloadMap();

        Value textSegmentValue = payload.getOrDefault(payloadTextKey, null);

        Map<String, Object> metadata = payload.entrySet().stream()
                .filter(entry -> !entry.getKey().equals(payloadTextKey))
                .collect(toMap(Map.Entry::getKey, entry -> ObjectFactory.object(entry.getValue())));

        Embedding embedding = extractDenseEmbedding(scoredPoint);

        // In HYBRID mode Qdrant returns RRF fusion scores (rank-based, typically
        // 0.01-0.05), not cosine similarity. Pass the raw score through so callers
        // can reason about it; minScore thresholds must be set accordingly.
        double score = searchMode == SearchMode.HYBRID
                ? scoredPoint.getScore()
                : RelevanceScore.fromCosineSimilarity(CosineSimilarity.between(embedding, referenceEmbedding));

        return new EmbeddingMatch<>(
                score,
                pointIdToString(scoredPoint.getId()),
                embedding,
                textSegmentValue == null
                        ? null
                        : TextSegment.from(textSegmentValue.getStringValue(), new Metadata(metadata)));
    }

    private Embedding extractDenseEmbedding(ScoredPoint scoredPoint) {
        if (!scoredPoint.hasVectors()) {
            throw new IllegalStateException("with_vectors=true but ScoredPoint has no vectors");
        }

        // Named vectors (HYBRID collections)
        if (scoredPoint.getVectors().hasVectors()) {
            Map<String, Points.VectorOutput> vectorsMap =
                    scoredPoint.getVectors().getVectors().getVectorsMap();
            Points.VectorOutput dense = vectorsMap.get(denseVectorName);
            if (dense != null) {
                return Embedding.from(dense.getDataList());
            }
        }

        // Unnamed single-vector collections
        if (scoredPoint.getVectors().hasVector()) {
            Points.VectorOutput v = scoredPoint.getVectors().getVector();
            List<Float> data = v.getDense().getDataList();
            if (data.isEmpty()) {
                data = v.getDataList();
            }
            return Embedding.from(data);
        }

        throw new IllegalStateException("ScoredPoint vectors present, but no dense vector data found");
    }

    private static void validateHybridConfig(SearchMode searchMode, SparseEncoder sparseEncoder) {
        if (searchMode == SearchMode.HYBRID && sparseEncoder == null) {
            throw new IllegalArgumentException("SparseEncoder is required for HYBRID search mode");
        }
    }

    private static void validateSparse(SparseVector sv) {
        if (sv == null || sv.indices() == null || sv.values() == null) {
            throw new IllegalArgumentException("Sparse vector is null");
        }
        if (sv.indices().size() != sv.values().size()) {
            throw new IllegalArgumentException("Sparse indices and values must have same length");
        }
        Set<Integer> uniq = new HashSet<>(sv.indices());
        if (uniq.size() != sv.indices().size()) {
            throw new IllegalArgumentException("Sparse indices must be unique");
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private String collectionName;
        private String host = "localhost";
        private int port = 6334;
        private boolean useTls = false;
        private String payloadTextKey = "text_segment";
        private String apiKey = null;
        private QdrantClient client = null;
        private SearchMode searchMode = SearchMode.VECTOR;
        private SparseEncoder sparseEncoder = null;
        private String denseVectorName = DEFAULT_DENSE_VECTOR_NAME;
        private String sparseVectorName = DEFAULT_SPARSE_VECTOR_NAME;

        /**
         * @param host The host of the Qdrant instance. Defaults to "localhost".
         */
        public Builder host(String host) {
            this.host = host;
            return this;
        }

        /**
         * @param collectionName REQUIRED. The name of the collection.
         */
        public Builder collectionName(String collectionName) {
            this.collectionName = collectionName;
            return this;
        }

        /**
         * @param port The GRPC port of the Qdrant instance. Defaults to 6334.
         * @return
         */
        public Builder port(int port) {
            this.port = port;
            return this;
        }

        /**
         * @param useTls Whether to use TLS(HTTPS). Defaults to false.
         * @return
         */
        public Builder useTls(boolean useTls) {
            this.useTls = useTls;
            return this;
        }

        /**
         * @param payloadTextKey The field name of the text segment in the payload.
         * Defaults to
         * "text_segment".
         * @return
         */
        public Builder payloadTextKey(String payloadTextKey) {
            this.payloadTextKey = payloadTextKey;
            return this;
        }

        /**
         * @param apiKey The Qdrant API key to authenticate with. Defaults to null.
         */
        public Builder apiKey(String apiKey) {
            this.apiKey = apiKey;
            return this;
        }

        /**
         * @param client A Qdrant client instance. Defaults to null.
         */
        public Builder client(QdrantClient client) {
            this.client = client;
            return this;
        }

        /**
         * @param searchMode {@link SearchMode#VECTOR} (default) or {@link SearchMode#HYBRID}.
         */
        public Builder searchMode(SearchMode searchMode) {
            this.searchMode = searchMode;
            return this;
        }

        /**
         * @param sparseEncoder Required when {@code searchMode} is
         * {@link SearchMode#HYBRID}.
         */
        public Builder sparseEncoder(SparseEncoder sparseEncoder) {
            this.sparseEncoder = sparseEncoder;
            return this;
        }

        /**
         * @param denseVectorName Named-vector key for the dense vector in HYBRID mode.
         * Defaults to "dense".
         */
        public Builder denseVectorName(String denseVectorName) {
            this.denseVectorName = denseVectorName;
            return this;
        }

        /**
         * @param sparseVectorName Named-vector key for the sparse vector in HYBRID mode.
         * Defaults to "sparse".
         */
        public Builder sparseVectorName(String sparseVectorName) {
            this.sparseVectorName = sparseVectorName;
            return this;
        }

        public QdrantEmbeddingStore build() {
            Objects.requireNonNull(collectionName, "collectionName cannot be null");

            if (client != null) {
                return new QdrantEmbeddingStore(
                        client,
                        collectionName,
                        payloadTextKey,
                        searchMode,
                        sparseEncoder,
                        denseVectorName,
                        sparseVectorName);
            }
            return new QdrantEmbeddingStore(
                    collectionName,
                    host,
                    port,
                    useTls,
                    payloadTextKey,
                    apiKey,
                    searchMode,
                    sparseEncoder,
                    denseVectorName,
                    sparseVectorName);
        }
    }

    private static PointId toPointId(String id) {
        try {
            long num = Long.parseUnsignedLong(id);
            return id(num);
        } catch (NumberFormatException e) {
            return id(UUID.fromString(id));
        }
    }

    private static String pointIdToString(PointId pointId) {
        return switch (pointId.getPointIdOptionsCase()) {
            case NUM -> Long.toUnsignedString(pointId.getNum());
            case UUID -> pointId.getUuid();
            default -> throw new IllegalStateException("Unknown point ID type: " + pointId.getPointIdOptionsCase());
        };
    }
}
