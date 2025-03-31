package dev.langchain4j.store.embedding.elasticsearch;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.BulkIndexByScrollFailure;
import co.elastic.clients.elasticsearch._types.ElasticsearchException;
import co.elastic.clients.elasticsearch._types.ErrorCause;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch.core.BulkRequest;
import co.elastic.clients.elasticsearch.core.BulkResponse;
import co.elastic.clients.elasticsearch.core.DeleteByQueryResponse;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.bulk.BulkResponseItem;
import co.elastic.clients.json.JsonpMapper;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.ElasticsearchTransport;
import co.elastic.clients.transport.rest_client.RestClientTransport;
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
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import static dev.langchain4j.internal.Utils.*;
import static dev.langchain4j.internal.ValidationUtils.*;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static java.util.stream.Collectors.toList;

/**
 * Represents an <a href="https://www.elastic.co/">Elasticsearch</a> index as an embedding store.
 * @see ElasticsearchConfigurationScript for the exact brute force implementation (slower - 100% accurate)
 * @see ElasticsearchConfigurationKnn for the knn search implementation (faster - approximative)
 * <br>
 * Supports storing {@link Metadata} and filtering by it using {@link Filter}
 * (provided inside {@link EmbeddingSearchRequest}).
 */
public class ElasticsearchEmbeddingStore implements EmbeddingStore<TextSegment> {

    private static final Logger log = LoggerFactory.getLogger(ElasticsearchEmbeddingStore.class);

    private final ElasticsearchConfiguration configuration;
    private final ElasticsearchClient client;
    private final String indexName;

    /**
     * Creates an instance of ElasticsearchEmbeddingStore.
     *
     * @param configuration Elasticsearch configuration to use (Knn or Script)
     * @param serverUrl     Elasticsearch Server URL (mandatory)
     * @param apiKey        Elasticsearch API key (optional)
     * @param userName      Elasticsearch userName (optional)
     * @param password      Elasticsearch password (optional)
     * @param indexName     Elasticsearch index name (optional). Default value: "default".
     *                      Index will be created automatically if not exists.
     * @param dimension     Embedding vector dimension (mandatory when index does not exist yet).
     * @deprecated by {@link ElasticsearchEmbeddingStore#ElasticsearchEmbeddingStore(ElasticsearchConfiguration, RestClient, String)}
     */
    @Deprecated(forRemoval = true)
    public ElasticsearchEmbeddingStore(ElasticsearchConfiguration configuration,
                                       String serverUrl,
                                       String apiKey,
                                       String userName,
                                       String password,
                                       String indexName,
                                       Integer dimension) {
        this(configuration, serverUrl, apiKey, userName, password, indexName);
        log.warn("Setting the dimension is deprecated.");
    }

    /**
     * Creates an instance of ElasticsearchEmbeddingStore.
     *
     * @param configuration Elasticsearch configuration to use (Knn or Script)
     * @param serverUrl     Elasticsearch Server URL (mandatory)
     * @param apiKey        Elasticsearch API key (optional)
     * @param userName      Elasticsearch userName (optional)
     * @param password      Elasticsearch password (optional)
     * @param indexName     Elasticsearch index name (optional). Default value: "default".
     *                      Index will be created automatically if not exists.
     * @deprecated by {@link ElasticsearchEmbeddingStore#ElasticsearchEmbeddingStore(ElasticsearchConfiguration, RestClient, String)}
     */
    @Deprecated(forRemoval = true)
    public ElasticsearchEmbeddingStore(ElasticsearchConfiguration configuration,
                                       String serverUrl,
                                       String apiKey,
                                       String userName,
                                       String password,
                                       String indexName) {

        this.configuration = configuration;

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
    }

    /**
     * Constructor using a RestClient
     *
     * @param configuration Elasticsearch configuration to use (Knn or Script)
     * @param restClient    Elasticsearch Rest Client (mandatory)
     * @param indexName     Elasticsearch index name (optional). Default value: "default".
     *                      Index will be created automatically if not exists.
     */
    public ElasticsearchEmbeddingStore(ElasticsearchConfiguration configuration, RestClient restClient, String indexName) {
        JsonpMapper mapper = new JacksonJsonpMapper();
        ElasticsearchTransport transport = new RestClientTransport(restClient, mapper);

        this.configuration = configuration;
        this.client = new ElasticsearchClient(transport);
        this.indexName = ensureNotNull(indexName, "indexName");
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
        private ElasticsearchConfiguration configuration = ElasticsearchConfigurationKnn.builder().build();

        /**
         * @param serverUrl Elasticsearch Server URL
         * @return builder
         * @deprecated call {@link #restClient(RestClient)} instead
         */
        @Deprecated(forRemoval = true)
        public Builder serverUrl(String serverUrl) {
            this.serverUrl = serverUrl;
            return this;
        }

        /**
         * @param apiKey Elasticsearch API key (optional)
         * @return builder
         * @deprecated call {@link #restClient(RestClient)} instead
         */
        @Deprecated(forRemoval = true)
        public Builder apiKey(String apiKey) {
            this.apiKey = apiKey;
            return this;
        }

        /**
         * @param userName Elasticsearch userName (optional)
         * @return builder
         * @deprecated call {@link #restClient(RestClient)} instead
         */
        @Deprecated(forRemoval = true)
        public Builder userName(String userName) {
            this.userName = userName;
            return this;
        }

        /**
         * @param password Elasticsearch password (optional)
         * @return builder
         * @deprecated call {@link #restClient(RestClient)} instead
         */
        @Deprecated(forRemoval = true)
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
         * @return builder
         */
        public Builder indexName(String indexName) {
            this.indexName = indexName;
            return this;
        }

        /**
         * @param dimension Embedding vector dimension.
         * @return builder
         * @deprecated dimension is not used anymore.
         */
        @Deprecated(forRemoval = true)
        public Builder dimension(Integer dimension) {
            log.warn("Setting the dimension is deprecated. This value is ignored.");
            return this;
        }

        /**
         * @param configuration the configuration to use
         * @return builder
         */
        public Builder configuration(ElasticsearchConfiguration configuration) {
            this.configuration = configuration;
            return this;
        }

        public ElasticsearchEmbeddingStore build() {
            if (restClient != null) {
                return new ElasticsearchEmbeddingStore(configuration, restClient, indexName);
            } else {
                log.warn("This is deprecated. You should provide a restClient instead and call ElasticsearchEmbeddingStore(ElasticsearchConfiguration, RestClient, String)");
                return new ElasticsearchEmbeddingStore(configuration, serverUrl, apiKey, userName, password, indexName);
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
        addAll(ids, embeddings, null);
        return ids;
    }

    @Override
    public EmbeddingSearchResult<TextSegment> search(EmbeddingSearchRequest embeddingSearchRequest) {
        log.debug("search([...{}...], {}, {})", embeddingSearchRequest.queryEmbedding().vector().length,
                embeddingSearchRequest.maxResults(), embeddingSearchRequest.minScore());
        try {
            SearchResponse<Document> response = configuration.internalSearch(client, indexName, embeddingSearchRequest);
            log.trace("found [{}] results", response);

            List<EmbeddingMatch<TextSegment>> results = toMatches(response);
            results.forEach(em -> log.debug("doc [{}] scores [{}]", em.embeddingId(), em.score()));
            return new EmbeddingSearchResult<>(results);
        } catch (ElasticsearchException | IOException e) {
            throw new ElasticsearchRequestFailedException(e);
        }
    }

    @Override
    public void removeAll(Collection<String> ids) {
        ensureNotEmpty(ids, "ids");
        removeByIds(ids);
    }

    @Override
    public void removeAll(Filter filter) {
        ensureNotNull(filter, "filter");
        Query query = ElasticsearchMetadataFilterMapper.map(filter);
        removeByQuery(query);
    }

    /**
     * The Elasticsearch implementation will simply drop the index instead
     * of removing all documents one by one.
     */
    @Override
    public void removeAll() {
        try {
            client.indices().delete(dir -> dir.index(indexName));
        } catch (ElasticsearchException e) {
            if (e.status() == 404) {
                log.debug("The index [{}] does not exist.", indexName);
            } else {
                throw new ElasticsearchRequestFailedException(e);
            }
        } catch (IOException e) {
            throw new ElasticsearchRequestFailedException(e);
        }
    }

    private void addInternal(String id, Embedding embedding, TextSegment embedded) {
        addAll(singletonList(id), singletonList(embedding), embedded == null ? null : singletonList(embedded));
    }

    @Override
    public void addAll(List<String> ids, List<Embedding> embeddings, List<TextSegment> embedded) {
        if (isNullOrEmpty(ids) || isNullOrEmpty(embeddings)) {
            log.info("[do not add empty embeddings to elasticsearch]");
            return;
        }
        ensureTrue(ids.size() == embeddings.size(), "ids size is not equal to embeddings size");
        ensureTrue(embedded == null || embeddings.size() == embedded.size(), "embeddings size is not equal to embedded size");

        try {
            bulkIndex(ids, embeddings, embedded);
        } catch (IOException e) {
            throw new ElasticsearchRequestFailedException(e);
        }
    }

    private void bulkIndex(List<String> ids, List<Embedding> embeddings, List<TextSegment> embedded) throws IOException {
        int size = ids.size();
        log.debug("calling bulkIndex with [{}] elements", size);
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
        handleBulkResponseErrors(response);
    }

    private void handleBulkResponseErrors(BulkResponse response) {
        if (response.errors()) {
            for (BulkResponseItem item : response.items()) {
                throwIfError(item.error());
            }
        }
    }

    private void throwIfError(ErrorCause errorCause) {
        if (errorCause != null) {
            throw new ElasticsearchRequestFailedException("type: " + errorCause.type() + ", reason: " + errorCause.reason());
        }
    }

    private void removeByQuery(Query query) {
        try {
            DeleteByQueryResponse response = client.deleteByQuery(delete -> delete
                    .index(indexName)
                    .query(query));
            if (!response.failures().isEmpty()) {
                for (BulkIndexByScrollFailure item : response.failures()) {
                    throwIfError(item.cause());
                }
            }
        } catch (IOException e) {
            throw new ElasticsearchRequestFailedException(e);
        }
    }

    private void removeByIds(Collection<String> ids) {
        try {
            bulkRemove(ids);
        } catch (IOException e) {
            throw new ElasticsearchRequestFailedException(e);
        }
    }

    private void bulkRemove(Collection<String> ids) throws IOException {
        BulkRequest.Builder bulkBuilder = new BulkRequest.Builder();
        for (String id : ids) {
            bulkBuilder.operations(op -> op.delete(dlt -> dlt
                    .index(indexName)
                    .id(id)));
        }
        BulkResponse response = client.bulk(bulkBuilder.build());
        handleBulkResponseErrors(response);
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
