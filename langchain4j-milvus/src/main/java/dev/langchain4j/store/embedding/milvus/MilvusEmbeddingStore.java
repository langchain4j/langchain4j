package dev.langchain4j.store.embedding.milvus;

import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.internal.Utils;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.filter.Filter;
import io.milvus.client.MilvusServiceClient;
import io.milvus.common.clientenum.ConsistencyLevelEnum;
import io.milvus.param.ConnectParam;
import io.milvus.param.IndexType;
import io.milvus.param.MetricType;
import io.milvus.param.dml.InsertParam;
import io.milvus.param.dml.SearchParam;
import io.milvus.response.SearchResultsWrapper;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.ValidationUtils.ensureNotEmpty;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;
import static dev.langchain4j.store.embedding.milvus.CollectionOperationsExecutor.*;
import static dev.langchain4j.store.embedding.milvus.CollectionRequestBuilder.buildSearchRequest;
import static dev.langchain4j.store.embedding.milvus.Generator.generateRandomIds;
import static dev.langchain4j.store.embedding.milvus.Mapper.*;
import static dev.langchain4j.store.embedding.milvus.MilvusMetadataFilterMapper.formatValues;
import static dev.langchain4j.store.embedding.milvus.MilvusMetadataFilterMapper.map;
import static io.milvus.common.clientenum.ConsistencyLevelEnum.EVENTUALLY;
import static io.milvus.param.IndexType.FLAT;
import static io.milvus.param.MetricType.COSINE;
import static java.lang.String.format;
import static java.util.Collections.singletonList;
import static java.util.stream.Collectors.toList;

/**
 * Represents an <a href="https://milvus.io/">Milvus</a> index as an embedding store.
 * <br>
 * Supports both local and <a href="https://zilliz.com/">managed</a> Milvus instances.
 * <br>
 * Supports storing {@link Metadata} and filtering by it using a {@link Filter}
 * (provided inside an {@link EmbeddingSearchRequest}).
 */
public class MilvusEmbeddingStore implements EmbeddingStore<TextSegment> {

    static final String ID_FIELD_NAME = "id";
    static final String TEXT_FIELD_NAME = "text";
    static final String METADATA_FIELD_NAME = "metadata";
    static final String VECTOR_FIELD_NAME = "vector";

    private final MilvusServiceClient milvusClient;
    private final String collectionName;
    private final MetricType metricType;
    private final ConsistencyLevelEnum consistencyLevel;
    private final boolean retrieveEmbeddingsOnSearch;
    private final boolean autoFlushOnInsert;

    public MilvusEmbeddingStore(
            String host,
            Integer port,
            String collectionName,
            Integer dimension,
            IndexType indexType,
            MetricType metricType,
            String uri,
            String token,
            String username,
            String password,
            ConsistencyLevelEnum consistencyLevel,
            Boolean retrieveEmbeddingsOnSearch,
            Boolean autoFlushOnInsert,
            String databaseName
    ) {
        ConnectParam.Builder connectBuilder = ConnectParam
                .newBuilder()
                .withHost(getOrDefault(host, "localhost"))
                .withPort(getOrDefault(port, 19530))
                .withUri(uri)
                .withToken(token)
                .withAuthorization(getOrDefault(username, ""), getOrDefault(password, ""));

        if (databaseName != null) {
            connectBuilder.withDatabaseName(databaseName);
        }

        this.milvusClient = new MilvusServiceClient(connectBuilder.build());
        this.collectionName = getOrDefault(collectionName, "default");
        this.metricType = getOrDefault(metricType, COSINE);
        this.consistencyLevel = getOrDefault(consistencyLevel, EVENTUALLY);
        this.retrieveEmbeddingsOnSearch = getOrDefault(retrieveEmbeddingsOnSearch, false);
        this.autoFlushOnInsert = getOrDefault(autoFlushOnInsert, false);

        if (!hasCollection(this.milvusClient, this.collectionName)) {
            createCollection(this.milvusClient, this.collectionName, ensureNotNull(dimension, "dimension"));
            createIndex(this.milvusClient, this.collectionName, getOrDefault(indexType, FLAT), this.metricType);
        }

        loadCollectionInMemory(this.milvusClient, collectionName);
    }

    public static Builder builder() {
        return new Builder();
    }

    public void dropCollection(String collectionName) {
        CollectionOperationsExecutor.dropCollection(this.milvusClient, collectionName);
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
        addAllInternal(ids, embeddings, null);
        return ids;
    }

    public List<String> addAll(List<Embedding> embeddings, List<TextSegment> embedded) {
        List<String> ids = generateRandomIds(embeddings.size());
        addAllInternal(ids, embeddings, embedded);
        return ids;
    }

    @Override
    public EmbeddingSearchResult<TextSegment> search(EmbeddingSearchRequest embeddingSearchRequest) {

        SearchParam searchParam = buildSearchRequest(
                collectionName,
                embeddingSearchRequest.queryEmbedding().vectorAsList(),
                embeddingSearchRequest.filter(),
                embeddingSearchRequest.maxResults(),
                metricType,
                consistencyLevel
        );

        SearchResultsWrapper resultsWrapper = CollectionOperationsExecutor.search(milvusClient, searchParam);

        List<EmbeddingMatch<TextSegment>> matches = toEmbeddingMatches(
                milvusClient,
                resultsWrapper,
                collectionName,
                consistencyLevel,
                retrieveEmbeddingsOnSearch
        );

        List<EmbeddingMatch<TextSegment>> result = matches.stream()
                .filter(match -> match.score() >= embeddingSearchRequest.minScore())
                .collect(toList());

        return new EmbeddingSearchResult<>(result);
    }

    private void addInternal(String id, Embedding embedding, TextSegment textSegment) {
        addAllInternal(
                singletonList(id),
                singletonList(embedding),
                textSegment == null ? null : singletonList(textSegment)
        );
    }

    private void addAllInternal(List<String> ids, List<Embedding> embeddings, List<TextSegment> textSegments) {
        List<InsertParam.Field> fields = new ArrayList<>();
        fields.add(new InsertParam.Field(ID_FIELD_NAME, ids));
        fields.add(new InsertParam.Field(TEXT_FIELD_NAME, toScalars(textSegments, ids.size())));
        fields.add(new InsertParam.Field(METADATA_FIELD_NAME, toMetadataJsons(textSegments, ids.size())));
        fields.add(new InsertParam.Field(VECTOR_FIELD_NAME, toVectors(embeddings)));

        insert(this.milvusClient, this.collectionName, fields);
        if (autoFlushOnInsert) {
            flush(this.milvusClient, this.collectionName);
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
        removeForVector(this.milvusClient, this.collectionName, format("%s in %s", ID_FIELD_NAME, formatValues(ids)));
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
        removeForVector(this.milvusClient, this.collectionName, map(filter));
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
        removeForVector(this.milvusClient, this.collectionName, format("%s != \"\"", ID_FIELD_NAME));
    }

    public static class Builder {

        private String host;
        private Integer port;
        private String collectionName;
        private Integer dimension;
        private IndexType indexType;
        private MetricType metricType;
        private String uri;
        private String token;
        private String username;
        private String password;
        private ConsistencyLevelEnum consistencyLevel;
        private Boolean retrieveEmbeddingsOnSearch;
        private String databaseName;
        private Boolean autoFlushOnInsert;

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
        public Builder indexType(IndexType indexType) {
            this.indexType = indexType;
            return this;
        }

        /**
         * @param metricType The type of the metric used for similarity search.
         *                   Default value: COSINE.
         * @return builder
         */
        public Builder metricType(MetricType metricType) {
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
        public Builder consistencyLevel(ConsistencyLevelEnum consistencyLevel) {
            this.consistencyLevel = consistencyLevel;
            return this;
        }

        /**
         * @param retrieveEmbeddingsOnSearch During a similarity search in Milvus (when calling findRelevant()),
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

        public MilvusEmbeddingStore build() {
            return new MilvusEmbeddingStore(
                    host,
                    port,
                    collectionName,
                    dimension,
                    indexType,
                    metricType,
                    uri,
                    token,
                    username,
                    password,
                    consistencyLevel,
                    retrieveEmbeddingsOnSearch,
                    autoFlushOnInsert,
                    databaseName
            );
        }
    }
}
