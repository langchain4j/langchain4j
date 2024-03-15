package dev.langchain4j.store.embedding.elasticsearch;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.InlineScript;
import co.elastic.clients.elasticsearch._types.KnnQuery;
import co.elastic.clients.elasticsearch._types.mapping.DenseVectorProperty;
import co.elastic.clients.elasticsearch._types.mapping.Property;
import co.elastic.clients.elasticsearch._types.mapping.TextProperty;
import co.elastic.clients.elasticsearch._types.mapping.TypeMapping;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch._types.query_dsl.ScriptScoreQuery;
import co.elastic.clients.elasticsearch.core.BulkRequest;
import co.elastic.clients.elasticsearch.core.BulkResponse;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.bulk.BulkResponseItem;
import co.elastic.clients.json.JsonData;
import co.elastic.clients.json.JsonpMapper;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.ElasticsearchTransport;
import co.elastic.clients.transport.endpoints.BooleanResponse;
import co.elastic.clients.transport.rest_client.RestClientTransport;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.filter.Filter;
import org.apache.http.Header;
import org.apache.http.HttpHost;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.message.BasicHeader;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestClientBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static dev.langchain4j.internal.Utils.isNullOrBlank;
import static dev.langchain4j.internal.Utils.isNullOrEmpty;
import static dev.langchain4j.internal.Utils.randomUUID;
import static dev.langchain4j.internal.ValidationUtils.ensureGreaterThanZero;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;
import static dev.langchain4j.internal.ValidationUtils.ensureTrue;
import static java.util.Collections.singletonList;
import static java.util.stream.Collectors.toList;

/**
 * Represents an <a href="https://www.elastic.co/">Elasticsearch</a> index as an embedding store.
 * Current implementation assumes the index uses the cosine distance metric.
 * <br>
 * Supports storing {@link Metadata} and filtering by it using {@link Filter}
 * (provided inside {@link EmbeddingSearchRequest}).
 */
public class ElasticsearchEmbeddingStore implements EmbeddingStore<TextSegment> {

    private static final Logger log = LoggerFactory.getLogger(ElasticsearchEmbeddingStore.class);

    private final ElasticsearchClient client;
    private final String indexName;
    private final ObjectMapper objectMapper;

    private final boolean ann;

    /**
     * Creates an instance of ElasticsearchEmbeddingStore.
     *
     * @param serverUrl Elasticsearch Server URL (mandatory)
     * @param apiKey    Elasticsearch API key (optional)
     * @param userName  Elasticsearch userName (optional)
     * @param password  Elasticsearch password (optional)
     * @param indexName Elasticsearch index name (optional). Default value: "default".
     *                  Index will be created automatically if not exists.
     * @param dimension Embedding vector dimension (mandatory when index does not exist yet).
     * @param ann       whether to enable Ann, the default is knn
     */
    public ElasticsearchEmbeddingStore(String serverUrl,
                                       String apiKey,
                                       String userName,
                                       String password,
                                       String indexName,
                                       Integer dimension,
                                       boolean ann) {

        RestClientBuilder restClientBuilder = RestClient
                .builder(HttpHost.create(ensureNotNull(serverUrl, "serverUrl")));

        if (!isNullOrBlank(userName)) {
            CredentialsProvider provider = new BasicCredentialsProvider();
            provider.setCredentials(AuthScope.ANY, new UsernamePasswordCredentials(userName, password));
            restClientBuilder.setHttpClientConfigCallback(httpClientBuilder -> httpClientBuilder.setDefaultCredentialsProvider(provider));
        }

        if (!isNullOrBlank(apiKey)) {
            restClientBuilder.setDefaultHeaders(new Header[]{
                    new BasicHeader("Authorization", "Apikey " + apiKey)
            });
        }

        ElasticsearchTransport transport = new RestClientTransport(restClientBuilder.build(), new JacksonJsonpMapper());

        this.client = new ElasticsearchClient(transport);
        this.indexName = ensureNotNull(indexName, "indexName");
        this.objectMapper = new ObjectMapper();
        this.ann = ann;

        createIndexIfNotExist(indexName, dimension);
    }

    public ElasticsearchEmbeddingStore(RestClient restClient, String indexName, Integer dimension, boolean ann) {
        JsonpMapper mapper = new JacksonJsonpMapper();
        ElasticsearchTransport transport = new RestClientTransport(restClient, mapper);

        this.client = new ElasticsearchClient(transport);
        this.indexName = ensureNotNull(indexName, "indexName");
        this.objectMapper = new ObjectMapper();
        this.ann = ann;

        createIndexIfNotExist(indexName, dimension);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private String serverUrl;
        private String apiKey;
        private String userName;
        private String password;
        private RestClient restClient;
        private String indexName = "default";
        private Integer dimension;
        private boolean ann;

        /**
         * @param serverUrl Elasticsearch Server URL
         * @return builder
         */
        public Builder serverUrl(String serverUrl) {
            this.serverUrl = serverUrl;
            return this;
        }

        /**
         * @param apiKey Elasticsearch API key (optional)
         * @return builder
         */
        public Builder apiKey(String apiKey) {
            this.apiKey = apiKey;
            return this;
        }

        /**
         * @param userName Elasticsearch userName (optional)
         * @return builder
         */
        public Builder userName(String userName) {
            this.userName = userName;
            return this;
        }

        /**
         * @param password Elasticsearch password (optional)
         * @return builder
         */
        public Builder password(String password) {
            this.password = password;
            return this;
        }

        /**
         * @param restClient Elasticsearch RestClient (optional).
         *                   Effectively overrides all other connection parameters like serverUrl, etc.
         * @return builder
         */
        public Builder restClient(RestClient restClient) {
            this.restClient = restClient;
            return this;
        }

        /**
         * @param indexName Elasticsearch index name (optional). Default value: "default".
         *                  Index will be created automatically if not exists.
         * @return builder
         */
        public Builder indexName(String indexName) {
            this.indexName = indexName;
            return this;
        }

        /**
         * @param dimension Embedding vector dimension (mandatory when index does not exist yet).
         * @return builder
         */
        public Builder dimension(Integer dimension) {
            this.dimension = dimension;
            return this;
        }

        /**
         * @param ann use approximate kNN or not.
         * @return builder
         */
        public Builder ann(boolean ann) {
            this.ann = ann;
            return this;
        }

        public ElasticsearchEmbeddingStore build() {
            if (restClient != null) {
                return new ElasticsearchEmbeddingStore(restClient, indexName, dimension, ann);
            } else {
                return new ElasticsearchEmbeddingStore(serverUrl, apiKey, userName, password, indexName, dimension, ann);
            }
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
        List<String> ids = embeddings.stream()
                .map(ignored -> randomUUID())
                .collect(toList());
        addAllInternal(ids, embeddings, null);
        return ids;
    }

    @Override
    public List<String> addAll(List<Embedding> embeddings, List<TextSegment> embedded) {
        List<String> ids = embeddings.stream()
                .map(ignored -> randomUUID())
                .collect(toList());
        addAllInternal(ids, embeddings, embedded);
        return ids;
    }

    /**
     * Elasticsearch supports two methods for kNN search:
     * <p>
     * Approximate kNN (Approximate Nearest Neighbor - ANN) using the knn search option or knn query
     * <p>
     * Exact, brute-force kNN using a script_score query with a vector function
     * <p>
     * In most cases, you’ll want to use approximate kNN. Approximate kNN offers lower latency at the cost of slower indexing and imperfect accuracy.
     * <p>
     * Exact, brute-force kNN guarantees accurate results but doesn’t scale well with large datasets. With this approach, a script_score query must scan each matching document to compute the vector function, which can result in slow search speeds.
     * <p>
     * However, you can improve latency by using a query to limit the number of matching documents passed to the function.
     * <p>
     * If you filter your data to a small subset of documents, you can get good search performance using this approach.
     * <p>
     *
     * @see <a href="https://www.elastic.co/guide/en/elasticsearch/reference/current/knn-search.html#tune-approximate-knn-for-speed-accuracy">k-nearest neighbor (kNN) search</a>
     */
    @Override
    public EmbeddingSearchResult<TextSegment> search(EmbeddingSearchRequest embeddingSearchRequest) {
        return ann ? new EmbeddingSearchResult<>(ann(embeddingSearchRequest)) : new EmbeddingSearchResult<>(knn(embeddingSearchRequest));
    }

    private List<EmbeddingMatch<TextSegment>> knn(EmbeddingSearchRequest embeddingSearchRequest) {
        try {
            // Use Script Score and cosineSimilarity to calculate
            // see https://www.elastic.co/guide/en/elasticsearch/reference/current/query-dsl-script-score-query.html#vector-functions-cosine
            ScriptScoreQuery scriptScoreQuery = buildScriptScoreQuery(
                    embeddingSearchRequest.queryEmbedding().vector(),
                    (float) embeddingSearchRequest.minScore(),
                    embeddingSearchRequest.filter()
            );
            SearchResponse<Document> response = client.search(
                    SearchRequest.of(s -> s.index(indexName)
                            .query(q -> q.scriptScore(scriptScoreQuery))
                            .size(embeddingSearchRequest.maxResults())),
                    Document.class
            );
            return toMatches(response);
        } catch (IOException e) {
            log.error("[ElasticSearch knn I/O Exception]", e);
            throw new ElasticsearchRequestFailedException(e.getMessage());
        }
    }

    private List<EmbeddingMatch<TextSegment>> ann(EmbeddingSearchRequest embeddingSearchRequest) {
        try {
            int maxResults = embeddingSearchRequest.maxResults();
            float[] vector = embeddingSearchRequest.queryEmbedding().vector();
            List<Float> vectorList = new ArrayList<>(vector.length);
            for (float value : vector) {
                vectorList.add(value);
            }
            // The score of each hit is the sum of the knn and query scores.
            // You can specify a boost value to give a weight to each score in the sum.
            // score = match_boost * match_score + knn_boost * knn_score
            Query query = buildQuery(embeddingSearchRequest.filter());
            KnnQuery knnQuery = KnnQuery.of(build -> build.field("vector").queryVector(vectorList).k(maxResults).numCandidates(10L * maxResults).boost(1f));
            SearchRequest searchRequest = SearchRequest.of(s -> s.index(indexName).knn(knnQuery).query(query).minScore(embeddingSearchRequest.minScore()));
            SearchResponse<Document> searchResponse = client.search(searchRequest, Document.class);
            return toMatches(searchResponse);
        } catch (IOException e) {
            log.error("[ElasticSearch ann I/O Exception]", e);
            throw new ElasticsearchRequestFailedException(e.getMessage());
        }
    }

    private ScriptScoreQuery buildScriptScoreQuery(float[] vector,
                                                   float minScore,
                                                   Filter filter) throws JsonProcessingException {
        return ScriptScoreQuery.of(q -> q.
                minScore(minScore)
                .query(buildQuery(filter))
                .script(s -> s.inline(InlineScript.of(i -> i
                        // The script adds 1.0 to the cosine similarity to prevent the score from being negative.
                        // divided by 2 to keep score in the range [0, 1]
                        .source("(cosineSimilarity(params.query_vector, 'vector') + 1.0) / 2")
                        .params("query_vector", toJsonData(vector))))
                )
        );
    }

    private Query buildQuery(Filter filter) {
        Query query;
        if (filter == null) {
            query = Query.of(q -> q.matchAll(m -> m.boost(0f)));
        } else {
            query = ElasticsearchMetadataFilterMapper.map(filter);
        }
        return query;
    }

    private <T> JsonData toJsonData(T rawData) {
        try {
            return JsonData.fromJson(objectMapper.writeValueAsString(rawData));
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    private void addInternal(String id, Embedding embedding, TextSegment embedded) {
        addAllInternal(singletonList(id), singletonList(embedding), embedded == null ? null : singletonList(embedded));
    }

    private void addAllInternal(List<String> ids, List<Embedding> embeddings, List<TextSegment> embedded) {
        if (isNullOrEmpty(ids) || isNullOrEmpty(embeddings)) {
            log.info("[do not add empty embeddings to elasticsearch]");
            return;
        }
        ensureTrue(ids.size() == embeddings.size(), "ids size is not equal to embeddings size");
        ensureTrue(embedded == null || embeddings.size() == embedded.size(), "embeddings size is not equal to embedded size");

        try {
            bulk(ids, embeddings, embedded);
        } catch (IOException e) {
            log.error("[ElasticSearch encounter I/O Exception]", e);
            throw new ElasticsearchRequestFailedException(e.getMessage());
        }
    }

    private void createIndexIfNotExist(String indexName, Integer dimension) {
        try {
            BooleanResponse response = client.indices().exists(c -> c.index(indexName));
            if (!response.value()) {
                ensureGreaterThanZero(dimension, "dimension");
                client.indices().create(c -> c.index(indexName)
                        .mappings(getDefaultMappings(dimension)));
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private TypeMapping getDefaultMappings(int dimension) {
        Map<String, Property> properties = new HashMap<>(4);
        properties.put("text", Property.of(p -> p.text(TextProperty.of(t -> t))));
        properties.put("vector", Property.of(p -> p.denseVector(DenseVectorProperty.of(d -> d.dims(dimension).similarity("cosine")))));
        return TypeMapping.of(c -> c.properties(properties));
    }

    private void bulk(List<String> ids, List<Embedding> embeddings, List<TextSegment> embedded) throws IOException {
        int size = ids.size();
        BulkRequest.Builder bulkBuilder = new BulkRequest.Builder();
        for (int i = 0; i < size; i++) {
            int finalI = i;
            Document document = Document.builder()
                    .vector(embeddings.get(i).vector())
                    .text(embedded == null ? null : embedded.get(i).text())
                    .metadata(embedded == null ? null : embedded.get(i).metadata().toMap())
                    .build();
            bulkBuilder.operations(op -> op.index(idx -> idx
                    .index(indexName)
                    .id(ids.get(finalI))
                    .document(document)));
        }

        BulkResponse response = client.bulk(bulkBuilder.build());
        if (response.errors()) {
            for (BulkResponseItem item : response.items()) {
                if (item.error() != null) {
                    throw new ElasticsearchRequestFailedException("type: " + item.error().type() + ", reason: " + item.error().reason());
                }
            }
        }
    }

    private List<EmbeddingMatch<TextSegment>> toMatches(SearchResponse<Document> response) {
        return response.hits().hits().stream()
                .map(hit -> Optional.ofNullable(hit.source())
                        .map(document -> new EmbeddingMatch<>(
                                hit.score(),
                                hit.id(),
                                new Embedding(document.getVector()),
                                document.getText() == null
                                        ? null
                                        : TextSegment.from(document.getText(), new Metadata(document.getMetadata()))
                        )).orElse(null))
                .collect(toList());
    }

}
