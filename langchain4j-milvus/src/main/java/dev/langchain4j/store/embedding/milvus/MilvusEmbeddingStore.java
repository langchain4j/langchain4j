package dev.langchain4j.store.embedding.milvus;

import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.Utils.isNullOrEmpty;
import static dev.langchain4j.internal.ValidationUtils.ensureNotEmpty;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;
import static dev.langchain4j.store.embedding.milvus.CollectionOperationsExecutor.createCollection;
import static dev.langchain4j.store.embedding.milvus.CollectionOperationsExecutor.createIndex;
import static dev.langchain4j.store.embedding.milvus.CollectionOperationsExecutor.flush;
import static dev.langchain4j.store.embedding.milvus.CollectionOperationsExecutor.hasCollection;
import static dev.langchain4j.store.embedding.milvus.CollectionOperationsExecutor.insert;
import static dev.langchain4j.store.embedding.milvus.CollectionOperationsExecutor.loadCollectionInMemory;
import static dev.langchain4j.store.embedding.milvus.CollectionOperationsExecutor.removeForVector;
import static dev.langchain4j.store.embedding.milvus.CollectionRequestBuilder.buildHybridSearchRequest;
import static dev.langchain4j.store.embedding.milvus.CollectionRequestBuilder.buildSearchRequest;
import static dev.langchain4j.store.embedding.milvus.Generator.generateRandomIds;
import static dev.langchain4j.store.embedding.milvus.Mapper.toEmbeddingMatches;
import static dev.langchain4j.store.embedding.milvus.Mapper.toMetadataJsons;
import static dev.langchain4j.store.embedding.milvus.Mapper.toScalars;
import static dev.langchain4j.store.embedding.milvus.Mapper.toSparseVectors;
import static dev.langchain4j.store.embedding.milvus.Mapper.toVectors;
import static dev.langchain4j.store.embedding.milvus.MilvusMetadataFilterMapper.formatValues;
import static dev.langchain4j.store.embedding.milvus.MilvusMetadataFilterMapper.map;
import static java.lang.String.format;
import static java.util.Collections.singletonList;
import static java.util.stream.Collectors.toList;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.internal.Utils;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.filter.Filter;
import io.milvus.v2.client.ConnectConfig;
import io.milvus.v2.client.MilvusClientV2;
import io.milvus.v2.common.ConsistencyLevel;
import io.milvus.v2.common.IndexParam;
import io.milvus.v2.service.vector.request.HybridSearchReq;
import io.milvus.v2.service.vector.request.SearchReq;
import io.milvus.v2.service.vector.request.ranker.BaseRanker;
import io.milvus.v2.service.vector.request.ranker.RRFRanker;
import io.milvus.v2.service.vector.response.SearchResp;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.SortedMap;
import java.util.TreeMap;

/**
 * Represents an <a href="https://milvus.io/">Milvus</a> index as an embedding store.
 * <br>
 * Supports both local and <a href="https://zilliz.com/">managed</a> Milvus instances.
 * <br>
 * Supports storing {@link Metadata} and filtering by it using a {@link Filter}
 * (provided inside an {@link EmbeddingSearchRequest}).
 */
public class MilvusEmbeddingStore implements EmbeddingStore<TextSegment> {

    private static final String DEFAULT_ID_FIELD_NAME = "id";
    private static final String DEFAULT_TEXT_FIELD_NAME = "text";
    private static final String DEFAULT_METADATA_FIELD_NAME = "metadata";
    private static final String DEFAULT_VECTOR_FIELD_NAME = "vector";
    private static final String DEFAULT_SPARSE_VECTOR_FIELD_NAME = "sparse_vector";

    private final MilvusClientV2 milvusClientV2;
    private final String collectionName;
    private final IndexParam.MetricType metricType;
    private final IndexParam.MetricType sparseMetricType;
    private final BaseRanker baseRanker;
    private final ConsistencyLevel consistencyLevel;
    private final boolean retrieveEmbeddingsOnSearch;
    private final boolean autoFlushOnInsert;
    private final FieldDefinition fieldDefinition;
    private final Integer dimension;
    private final MilvusSparseMode sparseMode;

    public MilvusEmbeddingStore(
            String host,
            Integer port,
            String collectionName,
            Integer dimension,
            IndexParam.IndexType indexType,
            IndexParam.IndexType sparseIndexType,
            IndexParam.MetricType metricType,
            IndexParam.MetricType sparseMetricType,
            String uri,
            String token,
            String username,
            String password,
            BaseRanker baseRanker,
            ConsistencyLevel consistencyLevel,
            Boolean retrieveEmbeddingsOnSearch,
            Boolean autoFlushOnInsert,
            String databaseName,
            String idFieldName,
            String textFieldName,
            String metadataFiledName,
            String vectorFiledName,
            String sparseVectorFieldName,
            MilvusSparseMode sparseMode) {
        this(
                createMilvusClient(host, port, uri, token, username, password, databaseName),
                collectionName,
                dimension,
                indexType,
                sparseIndexType,
                metricType,
                sparseMetricType,
                baseRanker,
                consistencyLevel,
                retrieveEmbeddingsOnSearch,
                autoFlushOnInsert,
                idFieldName,
                textFieldName,
                metadataFiledName,
                vectorFiledName,
                sparseVectorFieldName,
                sparseMode);
    }

    public MilvusEmbeddingStore(
            MilvusClientV2 milvusClientV2,
            String collectionName,
            Integer dimension,
            IndexParam.IndexType indexType,
            IndexParam.IndexType sparseIndexType,
            IndexParam.MetricType metricType,
            IndexParam.MetricType sparseMetricType,
            BaseRanker baseRanker,
            ConsistencyLevel consistencyLevel,
            Boolean retrieveEmbeddingsOnSearch,
            Boolean autoFlushOnInsert,
            String idFieldName,
            String textFieldName,
            String metadataFiledName,
            String vectorFiledName,
            String sparseVectorFieldName,
            MilvusSparseMode sparseMode) {
        this.milvusClientV2 = ensureNotNull(milvusClientV2, "milvusClientV2");
        this.collectionName = getOrDefault(collectionName, "default");
        this.metricType = getOrDefault(metricType, IndexParam.MetricType.COSINE);
        this.sparseMode = getOrDefault(sparseMode, MilvusSparseMode.BM25);
        if(this.sparseMode == MilvusSparseMode.BM25) {
            this.sparseMetricType = getOrDefault(sparseMetricType, IndexParam.MetricType.BM25);
        } else {
            this.sparseMetricType = getOrDefault(sparseMetricType, IndexParam.MetricType.IP);
        }
        this.baseRanker = getOrDefault(baseRanker, new RRFRanker(60));
        this.consistencyLevel = getOrDefault(consistencyLevel, ConsistencyLevel.EVENTUALLY);
        this.retrieveEmbeddingsOnSearch = getOrDefault(retrieveEmbeddingsOnSearch, false);
        this.autoFlushOnInsert = getOrDefault(autoFlushOnInsert, false);
        this.fieldDefinition = new FieldDefinition(
                getOrDefault(idFieldName, DEFAULT_ID_FIELD_NAME),
                getOrDefault(textFieldName, DEFAULT_TEXT_FIELD_NAME),
                getOrDefault(metadataFiledName, DEFAULT_METADATA_FIELD_NAME),
                getOrDefault(vectorFiledName, DEFAULT_VECTOR_FIELD_NAME),
                getOrDefault(sparseVectorFieldName, DEFAULT_SPARSE_VECTOR_FIELD_NAME));
        this.dimension = dimension;

        if (!hasCollection(this.milvusClientV2, this.collectionName)) {
            createCollection(
                    this.milvusClientV2,
                    this.collectionName,
                    this.fieldDefinition,
                    ensureNotNull(dimension, "dimension"),
                    this.sparseMode);
            createIndex(
                    this.milvusClientV2,
                    this.collectionName,
                    this.fieldDefinition.getVectorFieldName(),
                    getOrDefault(indexType, IndexParam.IndexType.FLAT),
                    this.metricType);
            if(this.sparseMode == MilvusSparseMode.BM25) {
                createIndex(
                        this.milvusClientV2,
                        this.collectionName,
                        this.fieldDefinition.getSparseVectorFieldName(),
                        IndexParam.IndexType.SPARSE_INVERTED_INDEX,
                        IndexParam.MetricType.BM25);
            } else {
                createIndex(
                        this.milvusClientV2,
                        this.collectionName,
                        this.fieldDefinition.getSparseVectorFieldName(),
                        getOrDefault(sparseIndexType, IndexParam.IndexType.SPARSE_INVERTED_INDEX),
                        this.sparseMetricType);
            }
        }

        loadCollectionInMemory(this.milvusClientV2, collectionName);
    }

    private static MilvusClientV2 createMilvusClient(
            String host,
            Integer port,
            String uri,
            String token,
            String username,
            String password,
            String databaseName) {
        String endpoint;
        if (uri != null && !uri.isBlank()) {
            endpoint = uri;
        } else {
            String h = getOrDefault(host, "localhost");
            int p = getOrDefault(port, 19530);
            endpoint = String.format("http://%s:%d", h, p);
        }

        ConnectConfig.ConnectConfigBuilder cfgB = ConnectConfig.builder().uri(endpoint);
        if (token != null && !token.isBlank()) {
            cfgB.token(token);
        } else if (username != null && !username.isBlank() && password != null && !password.isBlank()) {
            cfgB.username(username).password(password);
        }

        MilvusClientV2 client = new MilvusClientV2(cfgB.build());

        if (databaseName != null && !databaseName.isBlank()) {
            try {
                client.useDatabase(databaseName);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Interrupted while switching database to " + databaseName, e);
            }
        }

        return client;
    }

    public static Builder builder() {
        return new Builder();
    }

    public void dropCollection(String collectionName) {
        CollectionOperationsExecutor.dropCollection(this.milvusClientV2, collectionName);
    }

    public String add(Embedding embedding) {
        String id = Utils.randomUUID();
        add(id, embedding);
        return id;
    }

    public void add(String id, Embedding embedding) {
        addInternal(id, embedding, null);
    }

    public String add(Embedding embedding, TextSegment textSegment) {
        String id = Utils.randomUUID();
        addInternal(id, embedding, textSegment);
        return id;
    }

    public List<String> addAll(List<Embedding> embeddings) {
        List<String> ids = generateRandomIds(embeddings.size());
        addAll(ids, embeddings, null);
        return ids;
    }

    @Override
    public EmbeddingSearchResult<TextSegment> search(EmbeddingSearchRequest request) {
        MilvusEmbeddingSearchRequest milvusRequest;
        if (request instanceof MilvusEmbeddingSearchRequest) {
            milvusRequest = (MilvusEmbeddingSearchRequest) request;
        } else {
            milvusRequest = MilvusEmbeddingSearchRequest.milvusBuilder()
                    .queryEmbedding(request.queryEmbedding())
                    .maxResults(request.maxResults())
                    .minScore(request.minScore())
                    .filter(request.filter())
                    .build();
        }
        return search(milvusRequest);
    }


    public EmbeddingSearchResult<TextSegment> search(MilvusEmbeddingSearchRequest embeddingSearchRequest) {
        SearchResp searchResp;
        if (Objects.equals(embeddingSearchRequest.searchMode(), MilvusEmbeddingSearchMode.HYBRID)) {
            // Accept either manual sparse or auto text sparse
            boolean hasSparse = embeddingSearchRequest.sparseEmbedding() != null
                    || (embeddingSearchRequest.sparseQueryText() != null
                    && !embeddingSearchRequest.sparseQueryText().isBlank());
            // Validate that both dense and sparse embeddings are provided for hybrid search
            if (embeddingSearchRequest.queryEmbedding() == null || !hasSparse) {
                throw new IllegalArgumentException(
                        "HYBRID requires dense queryEmbedding and either sparseEmbedding or sparseQueryText");
            }

            HybridSearchReq hybridSearchReq = buildHybridSearchRequest(
                    embeddingSearchRequest,
                    collectionName,
                    fieldDefinition,
                    metricType,
                    sparseMetricType,
                    baseRanker,
                    consistencyLevel);
            searchResp = CollectionOperationsExecutor.search(milvusClientV2, hybridSearchReq);
        } else {
            SearchReq searchReq = buildSearchRequest(
                    embeddingSearchRequest,
                    collectionName,
                    fieldDefinition,
                    metricType,
                    sparseMetricType,
                    consistencyLevel);
            searchResp = CollectionOperationsExecutor.search(milvusClientV2, searchReq);
        }

        List<EmbeddingMatch<TextSegment>> matches = toEmbeddingMatches(
                milvusClientV2,
                searchResp,
                collectionName,
                fieldDefinition,
                consistencyLevel,
                retrieveEmbeddingsOnSearch);

        List<EmbeddingMatch<TextSegment>> result = matches.stream()
                .filter(match -> match.score() >= embeddingSearchRequest.minScore())
                .collect(toList());

        return new EmbeddingSearchResult<>(result);
    }

    private void addInternal(String id, Embedding embedding, TextSegment textSegment) {
        addAll(singletonList(id), singletonList(embedding), textSegment == null ? null : singletonList(textSegment));
    }

    @Override
    public void addAll(List<String> ids, List<Embedding> embeddings, List<TextSegment> textSegments) {
        if (isNullOrEmpty(ids) || isNullOrEmpty(embeddings)) {
            return;
        }

        List<String> textScalars = toScalars(textSegments, ids.size());
        List<JsonObject> metadataJsons = toMetadataJsons(textSegments, ids.size());
        List<List<Float>> denseVectors = toVectors(embeddings);
        Map<Long, Float> emptySparse = new TreeMap<>();

        List<JsonObject> rows = new ArrayList<>(ids.size());
        Gson gson = new Gson();

        for (int i = 0; i < ids.size(); i++) {
            JsonObject row = new JsonObject();

            row.addProperty(fieldDefinition.getIdFieldName(), ids.get(i));
            row.addProperty(fieldDefinition.getTextFieldName(), textScalars.get(i));
            row.add(fieldDefinition.getMetadataFieldName(), metadataJsons.get(i));
            row.add(fieldDefinition.getVectorFieldName(), gson.toJsonTree(denseVectors.get(i)));
            if(this.sparseMode == MilvusSparseMode.CUSTOM) {
                row.add(fieldDefinition.getSparseVectorFieldName(), gson.toJsonTree(emptySparse));
            }

            rows.add(row);
        }

        insert(this.milvusClientV2, this.collectionName, rows);
        if (autoFlushOnInsert) {
            flush(this.milvusClientV2, this.collectionName);
        }
    }

    public void addAllSparse(List<String> ids, List<SparseEmbedding> embeddings, List<TextSegment> textSegments) {
        if (isNullOrEmpty(ids) || isNullOrEmpty(embeddings)) {
            return;
        }

        if (this.sparseMode != MilvusSparseMode.CUSTOM) {
            throw new IllegalStateException("Built-in sparse mode does not accept client-provided sparse vectors.");
        }

        List<String> textScalars = toScalars(textSegments, ids.size());
        List<JsonObject> metadataJsons = toMetadataJsons(textSegments, ids.size());
        List<SortedMap<Long, Float>> sparseVectors = toSparseVectors(embeddings);
        List<Float> zeroDenseVectors = Collections.nCopies(this.dimension, 0f);

        List<JsonObject> rows = new ArrayList<>(ids.size());
        Gson gson = new Gson();

        for (int i = 0; i < ids.size(); i++) {
            JsonObject row = new JsonObject();

            row.addProperty(fieldDefinition.getIdFieldName(), ids.get(i));
            row.addProperty(fieldDefinition.getTextFieldName(), textScalars.get(i));
            row.add(fieldDefinition.getMetadataFieldName(), metadataJsons.get(i));
            row.add(fieldDefinition.getVectorFieldName(), gson.toJsonTree(zeroDenseVectors));
            row.add(fieldDefinition.getSparseVectorFieldName(), gson.toJsonTree(sparseVectors.get(i)));

            rows.add(row);
        }

        insert(this.milvusClientV2, this.collectionName, rows);
        if (autoFlushOnInsert) {
            flush(this.milvusClientV2, this.collectionName);
        }
    }

    public void addAllHybrid(
            List<String> ids,
            List<Embedding> denseEmbeddings,
            List<SparseEmbedding> sparseEmbeddings,
            List<TextSegment> textSegments) {
        if (isNullOrEmpty(ids) || isNullOrEmpty(denseEmbeddings) || isNullOrEmpty(sparseEmbeddings)) {
            return;
        }

        if (this.sparseMode != MilvusSparseMode.CUSTOM) {
            throw new IllegalStateException("Built-in sparse mode does not accept client-provided sparse vectors.");
        }

        List<String> textScalars = toScalars(textSegments, ids.size());
        List<JsonObject> metadataJsons = toMetadataJsons(textSegments, ids.size());
        List<List<Float>> denseVectors = toVectors(denseEmbeddings);
        List<SortedMap<Long, Float>> sparseVectors = toSparseVectors(sparseEmbeddings);

        List<JsonObject> rows = new ArrayList<>(ids.size());
        Gson gson = new Gson();

        for (int i = 0; i < ids.size(); i++) {
            JsonObject row = new JsonObject();

            row.addProperty(fieldDefinition.getIdFieldName(), ids.get(i));
            row.addProperty(fieldDefinition.getTextFieldName(), textScalars.get(i));
            row.add(fieldDefinition.getMetadataFieldName(), metadataJsons.get(i));
            row.add(fieldDefinition.getVectorFieldName(), gson.toJsonTree(denseVectors.get(i)));
            row.add(fieldDefinition.getSparseVectorFieldName(), gson.toJsonTree(sparseVectors.get(i)));

            rows.add(row);
        }

        insert(this.milvusClientV2, this.collectionName, rows);
        if (autoFlushOnInsert) {
            flush(this.milvusClientV2, this.collectionName);
        }
    }

    /**
     * Removes a single embedding from the store by ID.
     * <p>CAUTION</p>
     * <ul>
     *     <li>Deleted entities can still be retrieved immediately after the deletion if the consistency level is set lower than {@code Strong}</li>
     *     <li>Entities deleted beyond the pre-specified span of time for Time Travel cannot be retrieved again.</li>
     *     <li>Frequent deletion operations will impact the system performance.</li>
     *     <li>Before deleting entities by comlpex boolean expressions, make sure the collection has been loaded.</li>
     *     <li>Deleting entities by complex boolean expressions is not an atomic operation. Therefore, if it fails halfway through, some data may still be deleted.</li>
     *     <li>Deleting entities by complex boolean expressions is supported only when the consistency is set to Bounded. For details, <a href="https://milvus.io/docs/v2.3.x/consistency.md#Consistency-levels">see Consistency</a></li>
     * </ul>
     *
     * @param ids A collection of unique IDs of the embeddings to be removed.
     * @since Milvus version 2.3.x
     */
    @Override
    public void removeAll(Collection<String> ids) {
        ensureNotEmpty(ids, "ids");
        removeForVector(
                this.milvusClientV2,
                this.collectionName,
                format("%s in %s", this.fieldDefinition.getIdFieldName(), formatValues(ids)));
    }

    /**
     * Removes all embeddings that match the specified {@link Filter} from the store.
     * <p>CAUTION</p>
     * <ul>
     *     <li>Deleted entities can still be retrieved immediately after the deletion if the consistency level is set lower than {@code Strong}</li>
     *     <li>Entities deleted beyond the pre-specified span of time for Time Travel cannot be retrieved again.</li>
     *     <li>Frequent deletion operations will impact the system performance.</li>
     *     <li>Before deleting entities by comlpex boolean expressions, make sure the collection has been loaded.</li>
     *     <li>Deleting entities by complex boolean expressions is not an atomic operation. Therefore, if it fails halfway through, some data may still be deleted.</li>
     *     <li>Deleting entities by complex boolean expressions is supported only when the consistency is set to Bounded. For details, <a href="https://milvus.io/docs/v2.3.x/consistency.md#Consistency-levels">see Consistency</a></li>
     * </ul>
     *
     * @param filter The filter to be applied to the {@link Metadata} of the {@link TextSegment} during removal.
     *               Only embeddings whose {@code TextSegment}'s {@code Metadata}
     *               match the {@code Filter} will be removed.
     * @since Milvus version 2.3.x
     */
    @Override
    public void removeAll(Filter filter) {
        ensureNotNull(filter, "filter");
        removeForVector(
                this.milvusClientV2, this.collectionName, map(filter, this.fieldDefinition.getMetadataFieldName()));
    }

    /**
     * Removes all embeddings from the store.
     * <p>CAUTION</p>
     * <ul>
     *     <li>Deleted entities can still be retrieved immediately after the deletion if the consistency level is set lower than {@code Strong}</li>
     *     <li>Entities deleted beyond the pre-specified span of time for Time Travel cannot be retrieved again.</li>
     *     <li>Frequent deletion operations will impact the system performance.</li>
     *     <li>Before deleting entities by comlpex boolean expressions, make sure the collection has been loaded.</li>
     *     <li>Deleting entities by complex boolean expressions is not an atomic operation. Therefore, if it fails halfway through, some data may still be deleted.</li>
     *     <li>Deleting entities by complex boolean expressions is supported only when the consistency is set to Bounded. For details, <a href="https://milvus.io/docs/v2.3.x/consistency.md#Consistency-levels">see Consistency</a></li>
     * </ul>
     *
     * @since Milvus version 2.3.x
     */
    @Override
    public void removeAll() {
        removeForVector(
                this.milvusClientV2, this.collectionName, format("%s != \"\"", this.fieldDefinition.getIdFieldName()));
    }


    public enum MilvusSparseMode {
        BM25, // built-in sparse vector from text
        CUSTOM // user provided sparse vector
    }

    public static class Builder {
        private MilvusClientV2 milvusClientV2;
        private String host;
        private Integer port;
        private String collectionName;
        private Integer dimension;
        private IndexParam.IndexType indexType;
        private IndexParam.IndexType sparseIndexType;
        private IndexParam.MetricType metricType;
        private IndexParam.MetricType sparseMetricType;
        private String uri;
        private String token;
        private String username;
        private String password;
        private BaseRanker baseRanker;
        private ConsistencyLevel consistencyLevel;
        private Boolean retrieveEmbeddingsOnSearch;
        private String databaseName;
        private Boolean autoFlushOnInsert;
        private String idFieldName;
        private String textFieldName;
        private String metadataFieldName;
        private String vectorFieldName;
        private String sparseVectorFieldName;
        private MilvusSparseMode sparseMode;

        public Builder milvusClient(MilvusClientV2 milvusClientV2) {
            this.milvusClientV2 = milvusClientV2;
            return this;
        }

        /**
         * @param host The host of the self-managed Milvus instance.
         *             Default value: "localhost".
         * @return builder
         */
        public Builder host(String host) {
            this.host = host;
            return this;
        }

        /**
         * @param port The port of the self-managed Milvus instance.
         *             Default value: 19530.
         * @return builder
         */
        public Builder port(Integer port) {
            this.port = port;
            return this;
        }

        /**
         * @param collectionName The name of the Milvus collection.
         *                       If there is no such collection yet, it will be created automatically.
         *                       Default value: "default".
         * @return builder
         */
        public Builder collectionName(String collectionName) {
            this.collectionName = collectionName;
            return this;
        }

        /**
         * @param dimension The dimension of the embedding vector. (e.g. 384)
         *                  Mandatory if a new collection should be created.
         * @return builder
         */
        public Builder dimension(Integer dimension) {
            this.dimension = dimension;
            return this;
        }

        /**
         * @param indexType The type of the index.
         *                  Default value: FLAT.
         * @return builder
         */
        public Builder indexType(IndexParam.IndexType indexType) {
            this.indexType = indexType;
            return this;
        }

        /**
         * @param metricType The type of the metric used for similarity search.
         *                   Default value: COSINE.
         * @return builder
         */
        public Builder metricType(IndexParam.MetricType metricType) {
            this.metricType = metricType;
            return this;
        }

        /**
         * @param uri The URI of the managed Milvus instance. (e.g. "https://xxx.api.gcp-us-west1.zillizcloud.com")
         * @return builder
         */
        public Builder uri(String uri) {
            this.uri = uri;
            return this;
        }

        /**
         * @param token The token (API key) of the managed Milvus instance.
         * @return builder
         */
        public Builder token(String token) {
            this.token = token;
            return this;
        }

        /**
         * @param username The username. See details <a href="https://milvus.io/docs/authenticate.md">here</a>.
         * @return builder
         */
        public Builder username(String username) {
            this.username = username;
            return this;
        }

        /**
         * @param password The password. See details <a href="https://milvus.io/docs/authenticate.md">here</a>.
         * @return builder
         */
        public Builder password(String password) {
            this.password = password;
            return this;
        }

        /**
         * @param consistencyLevel The consistency level used by Milvus.
         *                         Default value: EVENTUALLY.
         * @return builder
         */
        public Builder consistencyLevel(ConsistencyLevel consistencyLevel) {
            this.consistencyLevel = consistencyLevel;
            return this;
        }

        /**
         * @param retrieveEmbeddingsOnSearch During a similarity search in Milvus (when calling search()),
         *                                   the embedding itself is not retrieved.
         *                                   To retrieve the embedding, an additional query is required.
         *                                   Setting this parameter to "true" will ensure that embedding is retrieved.
         *                                   Be aware that this will impact the performance of the search.
         *                                   Default value: false.
         * @return builder
         */
        public Builder retrieveEmbeddingsOnSearch(Boolean retrieveEmbeddingsOnSearch) {
            this.retrieveEmbeddingsOnSearch = retrieveEmbeddingsOnSearch;
            return this;
        }

        /**
         * @param autoFlushOnInsert Whether to automatically flush after each insert
         *                          ({@code add(...)} or {@code addAll(...)} methods).
         *                          Default value: false.
         *                          More info can be found
         *                          <a href="https://milvus.io/api-reference/pymilvus/v2.4.x/ORM/Collection/flush.md">here</a>.
         * @return builder
         */
        public Builder autoFlushOnInsert(Boolean autoFlushOnInsert) {
            this.autoFlushOnInsert = autoFlushOnInsert;
            return this;
        }

        /**
         * @param databaseName Milvus name of database.
         *                     Default value: null. In this case default Milvus database name will be used.
         * @return builder
         */
        public Builder databaseName(String databaseName) {
            this.databaseName = databaseName;
            return this;
        }

        /**
         * @param idFieldName the name of the field where the ID of the {@link Embedding} is stored.
         *                    Default value: "id".
         * @return builder
         */
        public Builder idFieldName(String idFieldName) {
            this.idFieldName = idFieldName;
            return this;
        }

        /**
         * @param textFieldName the name of the field where the text of the {@link TextSegment} is stored.
         *                      Default value: "text".
         * @return builder
         */
        public Builder textFieldName(String textFieldName) {
            this.textFieldName = textFieldName;
            return this;
        }

        /**
         * @param metadataFieldName the name of the field where the {@link Metadata} of the {@link TextSegment} is stored.
         *                          Default value: "metadata".
         * @return builder
         */
        public Builder metadataFieldName(String metadataFieldName) {
            this.metadataFieldName = metadataFieldName;
            return this;
        }

        /**
         * @param vectorFieldName the name of the field where the {@link Embedding} is stored.
         *                        Default value: "vector".
         * @return builder
         */
        public Builder vectorFieldName(String vectorFieldName) {
            this.vectorFieldName = vectorFieldName;
            return this;
        }

        /**
         * @param sparseVectorFieldName the name of the field where the {@link Embedding} is stored.
         *                              Default value: "sparse_vector".
         * @return builder
         */
        public Builder sparseVectorFieldName(String sparseVectorFieldName) {
            this.sparseVectorFieldName = sparseVectorFieldName;
            return this;
        }

        /**
         *
         * @param baseRanker the component that combines and reorders the similarity scores from multiple ANN sub-searches (e.g., dense and sparse) into a single final ranking.
         *                   Default value: new RRFRanker(60).
         * @return builder
         */
        public Builder baseRanker(BaseRanker baseRanker) {
            this.baseRanker = baseRanker;
            return this;
        }

        /**
         *
         * @param sparseMetricType The type of the metric used for sparse vector similarity search.
         *                         Default value: IP.
         * @return builer
         */
        public Builder sparseMetricType(IndexParam.MetricType sparseMetricType) {
            this.sparseMetricType = sparseMetricType;
            return this;
        }

        /**
         *
         * @param sparseIndexType  The type of the index.
         *                         Default value: SPARSE_INVERTED_INDEX.
         * @return builder
         */
        public Builder sparseIndexType(IndexParam.IndexType sparseIndexType) {
            this.sparseIndexType = sparseIndexType;
            return this;
        }

        /**
         *
         * @param sparseMode  The mode of sparse vector generation.
         *                    BM25 - use Milvus built-in sparse vector generation from text (sparseQueryText in search request must be provided).
         *                    CUSTOM - user provides sparse vector (sparseEmbedding in search request must be provided).
         *                    Default value: BM25.
         * @return builder
         */
        public Builder sparseMode(MilvusSparseMode sparseMode) {
            this.sparseMode = sparseMode;
            return this;
        }

        public MilvusEmbeddingStore build() {
            if (milvusClientV2 == null) {
                return new MilvusEmbeddingStore(
                        host,
                        port,
                        collectionName,
                        dimension,
                        indexType,
                        sparseIndexType,
                        metricType,
                        sparseMetricType,
                        uri,
                        token,
                        username,
                        password,
                        baseRanker,
                        consistencyLevel,
                        retrieveEmbeddingsOnSearch,
                        autoFlushOnInsert,
                        databaseName,
                        idFieldName,
                        textFieldName,
                        metadataFieldName,
                        vectorFieldName,
                        sparseVectorFieldName,
                        sparseMode);
            }
            return new MilvusEmbeddingStore(
                    milvusClientV2,
                    collectionName,
                    dimension,
                    indexType,
                    sparseIndexType,
                    metricType,
                    sparseMetricType,
                    baseRanker,
                    consistencyLevel,
                    retrieveEmbeddingsOnSearch,
                    autoFlushOnInsert,
                    idFieldName,
                    textFieldName,
                    metadataFieldName,
                    vectorFieldName,
                    sparseVectorFieldName,
                    sparseMode);
        }
    }
}
