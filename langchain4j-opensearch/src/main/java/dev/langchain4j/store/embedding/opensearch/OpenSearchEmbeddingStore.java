package dev.langchain4j.store.embedding.opensearch;

import com.fasterxml.jackson.core.JsonProcessingException;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.EmbeddingStore;
import org.apache.hc.client5.http.auth.AuthScope;
import org.apache.hc.client5.http.auth.UsernamePasswordCredentials;
import org.apache.hc.client5.http.impl.auth.BasicCredentialsProvider;
import org.apache.hc.client5.http.impl.nio.PoolingAsyncClientConnectionManagerBuilder;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.message.BasicHeader;
import org.opensearch.client.json.JsonData;
import org.opensearch.client.json.jackson.JacksonJsonpMapper;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.opensearch._types.ErrorCause;
import org.opensearch.client.opensearch._types.InlineScript;
import org.opensearch.client.opensearch._types.mapping.Property;
import org.opensearch.client.opensearch._types.mapping.TextProperty;
import org.opensearch.client.opensearch._types.mapping.TypeMapping;
import org.opensearch.client.opensearch._types.query_dsl.Query;
import org.opensearch.client.opensearch._types.query_dsl.ScriptScoreQuery;
import org.opensearch.client.opensearch.core.BulkRequest;
import org.opensearch.client.opensearch.core.BulkResponse;
import org.opensearch.client.opensearch.core.SearchRequest;
import org.opensearch.client.opensearch.core.SearchResponse;
import org.opensearch.client.opensearch.core.bulk.BulkResponseItem;
import org.opensearch.client.transport.OpenSearchTransport;
import org.opensearch.client.transport.aws.AwsSdk2Transport;
import org.opensearch.client.transport.aws.AwsSdk2TransportOptions;
import org.opensearch.client.transport.endpoints.BooleanResponse;
import org.opensearch.client.transport.httpclient5.ApacheHttpClient5TransportBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.http.SdkHttpClient;
import software.amazon.awssdk.http.apache.ApacheHttpClient;
import software.amazon.awssdk.regions.Region;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static dev.langchain4j.internal.Utils.*;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;
import static dev.langchain4j.internal.ValidationUtils.ensureTrue;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static java.util.stream.Collectors.toList;

/**
 * Represents an <a href="https://opensearch.org/">OpenSearch</a> index as an
 * embedding store. This implementation uses K-NN and the cosinesimil space type.
 */
public class OpenSearchEmbeddingStore implements EmbeddingStore<TextSegment> {

    private static final Logger log = LoggerFactory.getLogger(OpenSearchEmbeddingStore.class);

    private final String indexName;
    private final OpenSearchClient client;

    /**
     * Creates an instance of OpenSearchEmbeddingStore to connect with
     * OpenSearch clusters running locally and network reachable.
     *
     * @param serverUrl OpenSearch Server URL.
     * @param apiKey    OpenSearch API key (optional)
     * @param userName  OpenSearch username (optional)
     * @param password  OpenSearch password (optional)
     * @param indexName OpenSearch index name.
     */
    public OpenSearchEmbeddingStore(String serverUrl,
                                    String apiKey,
                                    String userName,
                                    String password,
                                    String indexName) {
        HttpHost openSearchHost;
        try {
            openSearchHost = HttpHost.create(serverUrl);
        } catch (URISyntaxException se) {
            log.error("[I/O OpenSearch Exception]", se);
            throw new OpenSearchRequestFailedException(se.getMessage());
        }

        OpenSearchTransport transport = ApacheHttpClient5TransportBuilder
                .builder(openSearchHost)
                .setMapper(new JacksonJsonpMapper())
                .setHttpClientConfigCallback(httpClientBuilder -> {

                    if (!isNullOrBlank(apiKey)) {
                        httpClientBuilder.setDefaultHeaders(singletonList(
                                new BasicHeader("Authorization", "ApiKey " + apiKey)
                        ));
                    }

                    if (!isNullOrBlank(userName) && !isNullOrBlank(password)) {
                        BasicCredentialsProvider credentialsProvider = new BasicCredentialsProvider();
                        credentialsProvider.setCredentials(new AuthScope(openSearchHost),
                                new UsernamePasswordCredentials(userName, password.toCharArray()));
                        httpClientBuilder.setDefaultCredentialsProvider(credentialsProvider);
                    }

                    httpClientBuilder.setConnectionManager(PoolingAsyncClientConnectionManagerBuilder.create().build());

                    return httpClientBuilder;
                })
                .build();

        this.client = new OpenSearchClient(transport);
        this.indexName = ensureNotNull(indexName, "indexName");
    }

    /**
     * Creates an instance of OpenSearchEmbeddingStore to connect with
     * OpenSearch clusters running as a fully managed service at AWS.
     *
     * @param serverUrl   OpenSearch Server URL.
     * @param serviceName The AWS signing service name, one of `es` (Amazon OpenSearch) or `aoss` (Amazon OpenSearch Serverless).
     * @param region      The AWS region for which requests will be signed. This should typically match the region in `serverUrl`.
     * @param options     The options to establish connection with the service. It must include which credentials should be used.
     * @param indexName   OpenSearch index name.
     */
    public OpenSearchEmbeddingStore(String serverUrl,
                                    String serviceName,
                                    String region,
                                    AwsSdk2TransportOptions options,
                                    String indexName) {

        Region selectedRegion = Region.of(region);

        SdkHttpClient httpClient = ApacheHttpClient.builder().build();
        OpenSearchTransport transport = new AwsSdk2Transport(httpClient, serverUrl, serviceName, selectedRegion, options);

        this.client = new OpenSearchClient(transport);
        this.indexName = ensureNotNull(indexName, "indexName");
    }

    /**
     * Creates an instance of OpenSearchEmbeddingStore using provided OpenSearchClient
     *
     * @param openSearchClient OpenSearch client provided
     * @param indexName        OpenSearch index name.
     */
    public OpenSearchEmbeddingStore(OpenSearchClient openSearchClient,
                                    String indexName) {

        this.client = ensureNotNull(openSearchClient, "openSearchClient");
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
        private String serviceName;
        private String region;
        private AwsSdk2TransportOptions options;
        private String indexName = "default";
        private OpenSearchClient openSearchClient;

        public Builder serverUrl(String serverUrl) {
            this.serverUrl = serverUrl;
            return this;
        }

        public Builder apiKey(String apiKey) {
            this.apiKey = apiKey;
            return this;
        }

        public Builder userName(String userName) {
            this.userName = userName;
            return this;
        }

        public Builder password(String password) {
            this.password = password;
            return this;
        }

        public Builder serviceName(String serviceName) {
            this.serviceName = serviceName;
            return this;
        }

        public Builder region(String region) {
            this.region = region;
            return this;
        }

        public Builder options(AwsSdk2TransportOptions options) {
            this.options = options;
            return this;
        }

        public Builder indexName(String indexName) {
            this.indexName = indexName;
            return this;
        }

        public Builder openSearchClient(OpenSearchClient openSearchClient) {
            this.openSearchClient = openSearchClient;
            return this;
        }
        public OpenSearchEmbeddingStore build() {
            if (openSearchClient != null) {
                return new OpenSearchEmbeddingStore(openSearchClient, indexName);
            }
            if (!isNullOrBlank(serviceName) && !isNullOrBlank(region) && options != null) {
                return new OpenSearchEmbeddingStore(serverUrl, serviceName, region, options, indexName);
            }
            return new OpenSearchEmbeddingStore(serverUrl, apiKey, userName, password, indexName);
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

    /**
     * This implementation uses the exact k-NN with scoring script to calculate
     * See https://opensearch.org/docs/latest/search-plugins/knn/knn-score-script/
     */
    @Override
    public EmbeddingSearchResult<TextSegment> search(EmbeddingSearchRequest request) {

        List<EmbeddingMatch<TextSegment>> matches;
        try {
            ScriptScoreQuery scriptScoreQuery = buildDefaultScriptScoreQuery(
                request.queryEmbedding().vector(),
                (float) request.minScore()
            );
            SearchResponse<Document> response = client.search(
                    SearchRequest.of(s -> s.index(indexName)
                            .query(n -> n.scriptScore(scriptScoreQuery))
                            .size(request.maxResults())),
                    Document.class
            );
            matches = toEmbeddingMatch(response);
        } catch (IOException ex) {
            log.error("[I/O OpenSearch Exception]", ex);
            throw new OpenSearchRequestFailedException(ex.getMessage());
        }

        return new EmbeddingSearchResult<>(matches);
    }

    private ScriptScoreQuery buildDefaultScriptScoreQuery(float[] vector, float minScore) throws JsonProcessingException {

        return ScriptScoreQuery.of(q -> q.minScore(minScore)
                .query(Query.of(qu -> qu.matchAll(m -> m)))
                .script(s -> s.inline(InlineScript.of(i -> i
                        .source("knn_score")
                        .lang("knn")
                        .params("field", JsonData.of("vector"))
                        .params("query_value", JsonData.of(vector))
                        .params("space_type", JsonData.of("cosinesimil")))))
                .boost(0.5f));

        // ===> From the OpenSearch documentation:
        // "Cosine similarity returns a number between -1 and 1, and because OpenSearch
        // relevance scores can't be below 0, the k-NN plugin adds 1 to get the final score."
        // See https://opensearch.org/docs/latest/search-plugins/knn/knn-score-script
        // Thus, the query applies a boost of `0.5` to keep score in the range [0, 1]
    }

    private void addInternal(String id, Embedding embedding, TextSegment embedded) {
        addAll(singletonList(id), singletonList(embedding), embedded == null ? null : singletonList(embedded));
    }

    @Override
    public void addAll(List<String> ids, List<Embedding> embeddings, List<TextSegment> embedded) {

        if (isNullOrEmpty(ids) || isNullOrEmpty(embeddings)) {
            log.info("[do not add empty embeddings to opensearch]");
            return;
        }

        ensureTrue(ids.size() == embeddings.size(), "ids size is not equal to embeddings size");
        ensureTrue(embedded == null || embeddings.size() == embedded.size(), "embeddings size is not equal to embedded size");

        try {
            createIndexIfNotExist(embeddings.get(0).dimension());
            bulk(ids, embeddings, embedded);
        } catch (IOException ex) {
            log.error("[I/O OpenSearch Exception]", ex);
            throw new OpenSearchRequestFailedException(ex.getMessage());
        }
    }

    private void createIndexIfNotExist(int dimension) throws IOException {
        BooleanResponse response = client.indices().exists(c -> c.index(indexName));
        if (!response.value()) {
            client.indices()
                    .create(c -> c.index(indexName)
                            .settings(s -> s.knn(true))
                            .mappings(getDefaultMappings(dimension)));
        }
    }

    private TypeMapping getDefaultMappings(int dimension) {
        Map<String, Property> properties = new HashMap<>(4);
        properties.put("text", Property.of(p -> p.text(TextProperty.of(t -> t))));
        properties.put("vector", Property.of(p -> p.knnVector(
                k -> k.dimension(dimension)
        )));
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
                    .metadata(embedded == null ? null : Optional.ofNullable(embedded.get(i).metadata())
                            .map(Metadata::toMap)
                            .orElse(null))
                    .build();
            bulkBuilder.operations(op -> op.index(
                    idx -> idx
                            .index(indexName)
                            .id(ids.get(finalI))
                            .document(document)
            ));
        }

        BulkResponse bulkResponse = client.bulk(bulkBuilder.build());

        if (bulkResponse.errors()) {
            for (BulkResponseItem item : bulkResponse.items()) {
                if (item.error() != null) {
                    ErrorCause errorCause = item.error();
                    if (errorCause != null) {
                        throw new OpenSearchRequestFailedException(
                                "type: " + errorCause.type() + "," +
                                        "reason: " + errorCause.reason());
                    }
                }
            }
        }
    }

    private List<EmbeddingMatch<TextSegment>> toEmbeddingMatch(SearchResponse<Document> response) {
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
